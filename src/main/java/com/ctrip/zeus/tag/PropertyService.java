package com.ctrip.zeus.tag;

import com.ctrip.zeus.service.query.command.QueryCommand;
import com.ctrip.zeus.tag.entity.Property;

import java.util.List;
import java.util.Set;

/**
 * Created by zhoumy on 2015/7/21.
 */
public interface PropertyService {

    Set<Long> queryByCommand(QueryCommand command, String type) throws Exception;

    List<Property> getAllProperties() throws Exception;

    List<Property> getProperties(String type, Long itemId) throws Exception;

    Property getProperty(String pname, Long itemId, String type) throws Exception;

    Set<Long> unionQuery(List<Property> properties, String type) throws Exception;

    Set<Long> joinQuery(List<Property> properties, String type) throws Exception;

    List<Long> queryTargets(String pname, String pvalue, String type) throws Exception;
}
