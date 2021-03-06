package com.ctrip.zeus.service.status.impl;

import com.ctrip.zeus.dal.core.ConfSlbActiveDao;
import com.ctrip.zeus.model.entity.Group;
import com.ctrip.zeus.model.entity.GroupServer;
import com.ctrip.zeus.service.model.*;
import com.ctrip.zeus.service.query.GroupCriteriaQuery;
import com.ctrip.zeus.service.query.SlbCriteriaQuery;
import com.ctrip.zeus.service.query.VirtualServerCriteriaQuery;
import com.ctrip.zeus.service.status.GroupStatusService;
import com.ctrip.zeus.service.status.StatusOffset;
import com.ctrip.zeus.service.status.StatusService;
import com.ctrip.zeus.status.entity.GroupServerStatus;
import com.ctrip.zeus.status.entity.GroupStatus;
import com.netflix.config.DynamicBooleanProperty;
import com.netflix.config.DynamicPropertyFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.*;

/**
 * User: mag
 * Date: 4/1/2015
 * Time: 2:26 PM
 */
@Service("groupStatusService")
public class GroupStatusServiceImpl implements GroupStatusService {

    @Resource
    SlbRepository slbRepository;
    @Resource
    SlbCriteriaQuery slbCriteriaQuery;
    @Resource
    GroupCriteriaQuery groupCriteriaQuery;
    @Resource
    VirtualServerCriteriaQuery virtualServerCriteriaQuery;
    @Resource
    GroupRepository groupRepository;
    @Resource
    StatusService statusService;
    @Resource
    ConfSlbActiveDao confSlbActiveDao;
    @Resource
    EntityFactory entityFactory;


    private Logger LOGGER = LoggerFactory.getLogger(GroupStatusServiceImpl.class);

    @Override
    public List<GroupStatus> getAllOnlineGroupsStatus() throws Exception {
        List<GroupStatus> result = new ArrayList<>();
        Set<IdVersion> slbIds = slbCriteriaQuery.queryAll(SelectionMode.ONLINE_EXCLUSIVE);
        for (IdVersion slb : slbIds) {
            result.addAll(getOnlineGroupsStatusBySlbId(slb.getId()));
        }
        return result;
    }

    @Override
    public List<GroupStatus> getAllOfflineGroupsStatus() throws Exception {
        List<GroupStatus> result = new ArrayList<>();
        Set<IdVersion> slbIds = slbCriteriaQuery.queryAll(SelectionMode.OFFLINE_FIRST);
        for (IdVersion slb : slbIds) {
            result.addAll(getOfflineGroupsStatusBySlbId(slb.getId()));
        }
        return result;
    }

    @Override
    public List<GroupStatus> getOnlineGroupsStatusBySlbId(Long slbId) throws Exception {
        List<GroupStatus> result = new ArrayList<>();
        Long[] vses = entityFactory.getVsIdsBySlbId(slbId, SelectionMode.ONLINE_EXCLUSIVE);
        ModelStatusMapping<Group> groups = entityFactory.getGroupsByVsIds(vses);
        if (groups.getOnlineMapping() == null || groups.getOnlineMapping().size() == 0) {
            return result;
        }
        result = getOnlineGroupsStatus(groups.getOnlineMapping());
        return result;
    }

    @Override
    public List<GroupStatus> getOfflineGroupsStatusBySlbId(Long slbId) throws Exception {
        List<GroupStatus> result = new ArrayList<>();
        Long[] vses = entityFactory.getVsIdsBySlbId(slbId, SelectionMode.OFFLINE_FIRST);
        ModelStatusMapping<Group> groups = entityFactory.getGroupsByVsIds(vses);
        if (groups.getOfflineMapping() == null || groups.getOfflineMapping().size() == 0) {
            return result;
        }
        result = getOfflineGroupsStatus(groups.getOfflineMapping(), groups.getOnlineMapping());
        return result;
    }

    @Override
    public GroupStatus getOnlineGroupStatus(Long groupId) throws Exception {
        GroupStatus result = null;
        ModelStatusMapping<Group> groupMap = entityFactory.getGroupsByIds(new Long[]{groupId});
        if (groupMap.getOnlineMapping().size() == 0) {
            return result;
        }
        List<GroupStatus> list = getOnlineGroupsStatus(groupMap.getOnlineMapping());
        if (list.size() > 0) {
            result = list.get(0);
        }
        return result;
    }

    @Override
    public GroupStatus getOfflineGroupStatus(Long groupId) throws Exception {
        GroupStatus result = null;
        ModelStatusMapping<Group> groupMap = entityFactory.getGroupsByIds(new Long[]{groupId});
        if (groupMap.getOfflineMapping().size() == 0) {
            return result;
        }

        List<GroupStatus> list = getOfflineGroupsStatus(groupMap.getOfflineMapping(), groupMap.getOnlineMapping());
        if (list.size() > 0) {
            result = list.get(0);
        }
        return result;
    }

    @Override
    public List<GroupStatus> getOfflineGroupsStatus(Set<Long> groupIds) throws Exception {
        List<GroupStatus> result = new ArrayList<>();
        ModelStatusMapping<Group> map = entityFactory.getGroupsByIds(groupIds.toArray(new Long[]{}));
        if (map.getOfflineMapping() == null || map.getOfflineMapping().size() == 0) {
            return result;
        }
        result = getOfflineGroupsStatus(map.getOfflineMapping(), map.getOnlineMapping());
        return result;
    }

    @Override
    public List<GroupStatus> getOnlineGroupsStatus(Map<Long, Group> groups) throws Exception {
        List<GroupStatus> res = new ArrayList<>();
        GroupStatus status = new GroupStatus();

        Map<String, List<Boolean>> memberStatus = statusService.fetchGroupServerStatus(groups.keySet().toArray(new Long[]{}));
        Set<String> allDownServers = statusService.findAllDownServers();
        for (Group group : groups.values()) {
            Long groupId = group.getId();
            status.setGroupId(groupId);
            status.setGroupName(group.getName());
            status.setActivated(true);

            List<GroupServer> groupServerList = group.getGroupServers();
            for (GroupServer gs : groupServerList) {
                GroupServerStatus groupServerStatus = new GroupServerStatus();
                groupServerStatus.setIp(gs.getIp());
                groupServerStatus.setPort(gs.getPort());
                String key = groupId + "_" + gs.getIp();
                if (memberStatus.get(key) == null) {
                    LOGGER.error("[StatusError]Group Member Status is missing. groupId:" + groupId + "ip:" + gs.getIp());
                    continue;
                }
                boolean memberUp = memberStatus.get(key).get(StatusOffset.MEMBER_OPS);
                boolean serverUp = !allDownServers.contains(gs.getIp());
                boolean pullIn = memberStatus.get(key).get(StatusOffset.PULL_OPS);
                boolean raise = memberStatus.get(key).get(StatusOffset.HEALTHY);
                boolean up =  memberUp && serverUp && pullIn && raise;

                groupServerStatus.setServer(serverUp);
                groupServerStatus.setMember(memberUp);
                groupServerStatus.setPull(pullIn);
                groupServerStatus.setHealthy(raise);
                groupServerStatus.setUp(up);
                groupServerStatus.setOnline(true);
                status.addGroupServerStatus(groupServerStatus);
            }
            res.add(status);
        }
        return res;
    }

    @Override
    public List<GroupStatus> getOfflineGroupsStatus(Map<Long, Group> groups, Map<Long, Group> onlineGroups) throws Exception {
        List<GroupStatus> res = new ArrayList<>();

        Map<String, List<Boolean>> memberStatus = statusService.fetchGroupServerStatus(groups.keySet().toArray(new Long[]{}));
        Set<String> allDownServers = statusService.findAllDownServers();

        for (Group group : groups.values()) {
            GroupStatus status = new GroupStatus();
            Long groupId = group.getId();
            status.setGroupId(groupId);
            status.setGroupName(group.getName());
            status.setActivated(onlineGroups.containsKey(groupId));

            Group onlineGroup = onlineGroups.get(groupId);
            Set<String> onlineMembers = new HashSet<>();
            Set<String> offlineMembers = new HashSet<>();
            Map<String, GroupServer> members = new HashMap<>();
            if (onlineGroup != null) {
                for (GroupServer groupServer : onlineGroup.getGroupServers()) {
                    onlineMembers.add(groupServer.getIp());
                    members.put(groupServer.getIp(), groupServer);
                }
            }
            List<GroupServer> groupServerList = group.getGroupServers();
            for (GroupServer gs : groupServerList) {
                offlineMembers.add(gs.getIp());
                members.put(gs.getIp(), gs);
            }
            for (GroupServer gs : members.values()) {
                GroupServerStatus groupServerStatus = new GroupServerStatus();
                groupServerStatus.setIp(gs.getIp());
                groupServerStatus.setPort(gs.getPort());
                String key = groupId + "_" + gs.getIp();
                if (memberStatus.get(key) == null) {
                    LOGGER.error("[StatusError]Group Member Status is missing. groupId:" + groupId + "ip:" + gs.getIp());
                    continue;
                }
                boolean memberUp = memberStatus.get(key).get(StatusOffset.MEMBER_OPS);
                boolean serverUp = !allDownServers.contains(gs.getIp());
                boolean pullIn = memberStatus.get(key).get(StatusOffset.PULL_OPS);
                boolean raise = memberStatus.get(key).get(StatusOffset.HEALTHY);
                boolean up = memberUp && serverUp && pullIn && raise;
                NextStatus nextStatus = NextStatus.Online;
                boolean online = onlineMembers.contains(gs.getIp());
                if (!online) {
                    up = false;
                }
                if (onlineMembers.contains(gs.getIp()) && offlineMembers.contains(gs.getIp())) {
                    nextStatus = NextStatus.Online;
                } else if (onlineMembers.contains(gs.getIp()) && !offlineMembers.contains(gs.getIp())) {
                    nextStatus = NextStatus.ToBeOffline;
                } else if (!onlineMembers.contains(gs.getIp()) && offlineMembers.contains(gs.getIp())) {
                    nextStatus = NextStatus.ToBeOnline;
                }
                groupServerStatus.setServer(serverUp);
                groupServerStatus.setMember(memberUp);
                groupServerStatus.setPull(pullIn);
                groupServerStatus.setHealthy(raise);
                groupServerStatus.setUp(up);
                groupServerStatus.setOnline(onlineMembers.contains(gs.getIp()));
                groupServerStatus.setNextStatus(nextStatus.getName());
                status.addGroupServerStatus(groupServerStatus);
            }
            res.add(status);
        }
        return res;
    }

    enum NextStatus {
        Online,
        ToBeOnline,
        ToBeOffline;

        String getName() {
            return name();
        }
    }
}
