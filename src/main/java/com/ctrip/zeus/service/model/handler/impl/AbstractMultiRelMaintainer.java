package com.ctrip.zeus.service.model.handler.impl;

import com.ctrip.zeus.service.model.IdVersion;
import com.ctrip.zeus.service.model.handler.MultiRelMaintainer;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.*;

/**
 * Created by zhoumy on 2015/12/22.
 */
public abstract class AbstractMultiRelMaintainer<T, W, X> implements MultiRelMaintainer<W, X> {
    protected static final int OFFSET_OFFLINE = 0;
    protected static final int OFFSET_ONLINE = 1;
    private static final Map<Class, Method> MethodCache = new HashMap<>();

    protected final Class<T> clazzT;

    private Method m_getId;

    protected AbstractMultiRelMaintainer(Class<T> clazzT, Class<X> clazzX) {
        this.clazzT = clazzT;
        m_getId = MethodCache.get(clazzX);
        if (m_getId == null) {
            try {
                m_getId = clazzX.getMethod("getId");
                MethodCache.put(clazzX, m_getId);
            } catch (NoSuchMethodException e) {
            }
        }
    }

    @Override
    public void addRel(X object) throws Exception {
        List<W> rels = getRelations(object);
        T[] dos = (T[]) Array.newInstance(clazzT, rels.size());
        for (int i = 0; i < dos.length; i++) {
            T d = clazzT.newInstance();
            setDo(object, rels.get(i), d);
            dos[i] = d;
        }
        insert(dos);
    }

    @Override
    public void updateRel(X object) throws Exception {
        List<W> rels = getRelations(object);
        Integer[] versions = getStatusByObjectId(object);
        if (versions[OFFSET_OFFLINE].intValue() == versions[OFFSET_ONLINE].intValue()) {
            addRel(object);
            return;
        }
        int offlineVersion = versions[OFFSET_OFFLINE];
        List<T> offline = new ArrayList<>();
        for (T t : getRelsByObjectId(object)) {
            if (getIdxKey(t).getVersion().intValue() == offlineVersion) {
                offline.add(t);
            }
        }

        Map<String, List<T>> actionMap = groupByAction(object, rels, offline, clazzT);
        List<T> action = actionMap.get("update");
        if (action != null) updateByPrimaryKey(action.toArray((T[]) Array.newInstance(clazzT, action.size())));

        action = actionMap.get("delete");
        if (action != null) deleteByPrimaryKey(action.toArray((T[]) Array.newInstance(clazzT, action.size())));

        action = actionMap.get("insert");
        if (action != null) insert(action.toArray((T[]) Array.newInstance(clazzT, action.size())));
//        int i = 0;
//        Iterator<T> iter = update.iterator();
//        while (iter.hasNext() && i < rels.size()) {
//            setDo(object, rels.get(i), iter.next());
//            i++;
//        }
//
//        final int offset = i;
//        if (offset > 0) {
//            updateByPrimaryKey(update.subList(0, offset).toArray((T[]) Array.newInstance(clazzT, offset)));
//        }
//        if (offset < update.size()) {
//            deleteByPrimaryKey(update.subList(offset, update.size()).toArray((T[]) Array.newInstance(clazzT, update.size() - offset + 1)));
//        }
//        if (offset < rels.size()) {
//            T[] dos = (T[]) Array.newInstance(clazzT, rels.size() - offset + 1);
//            for (int j = offset; j < dos.length; j++) {
//                dos[j - offset] = getDo(object, rels.get(j));
//            }
//            insert(dos);
//        }
    }

    @Override
    public void updateStatus(X[] objects) throws Exception {
        Long[] ids = new Long[objects.length];
        Map<Long, Integer> idx = new HashMap<>();

        List<T>[] dosRef = new List[objects.length];
        List<Integer[]> versionRef = new ArrayList<>(objects.length);
        Integer[] initValue = new Integer[]{0, 0};

        for (int i = 0; i < objects.length; i++) {
            Long id = getObjectId(objects[i]);
            idx.put(id, i);
            ids[i] = id;
            dosRef[i] = new ArrayList<>();
            versionRef.add(initValue);
        }

        for (T d : getRelsByObjectId(ids)) {
            dosRef[idx.get(getIdxKey(d).getId())].add(d);
        }
        for (Map.Entry<Long, Integer[]> e : getStatusByObjectId(ids).entrySet()) {
            versionRef.set(idx.get(e.getKey()), e.getValue());
        }

        Map<String, List<T>> actionMap = new HashMap<>();
        List<T> add = new ArrayList<>();

        for (int i = 0; i < objects.length; i++) {
            X object = objects[i];
            List<W> rels = getRelations(object);
            Integer[] versions = versionRef.get(i);
            if (versions[OFFSET_OFFLINE].intValue() == versions[OFFSET_ONLINE].intValue()) {
                for (W w : rels) {
                    T d = clazzT.newInstance();
                    setDo(object, w, d);
                    if (getIdxKey(d).getVersion() != 0) add.add(d);
                }
                continue;
            }

            int onlineVersion = versions[OFFSET_ONLINE];
            List<T> online = new ArrayList<>();
            for (T t : dosRef[i]) {
                if (getIdxKey(t).getVersion() == onlineVersion) {
                    online.add(t);
                }
            }
            actionMap.putAll(groupByAction(object, rels, online, clazzT));
        }

        List<T> action = actionMap.get("update");
        if (action != null) updateByPrimaryKey(action.toArray((T[]) Array.newInstance(clazzT, action.size())));

        action = actionMap.get("delete");
        if (action != null) deleteByPrimaryKey(action.toArray((T[]) Array.newInstance(clazzT, action.size())));

        action = actionMap.get("insert");
        if (action != null) add.addAll(action);
        if (action != null) insert(add.toArray((T[]) Array.newInstance(clazzT, add.size())));
    }

    protected Map<String, List<T>> groupByAction(X object, List<W> rels, List<T> update, Class<T> clazzT) throws Exception {
        Map<String, List<T>> result = new HashMap<>();
        int i = 0;
        Iterator<T> iter = update.iterator();
        while (iter.hasNext() && i < rels.size()) {
            setDo(object, rels.get(i), iter.next());
            i++;
        }

        final int offset = i;
        if (offset > 0) {
            result.put("update", update.subList(0, offset));
        }
        if (offset < update.size()) {
            result.put("delete", update.subList(offset, update.size()));
        }
        if (offset < rels.size()) {
            List<T> dos = new ArrayList<>();
            for (int j = offset; j < rels.size(); j++) {
                T d = clazzT.newInstance();
                setDo(object, rels.get(j), d);
                dos.add(d);
            }
        }
        return result;
    }

    protected Long getObjectId(X object) {
        if (m_getId != null) {
            try {
                return (Long) m_getId.invoke(object);
            } catch (Exception e) {
            }
        }
        return 0L;
    }

    protected abstract IdVersion getIdxKey(T rel) throws Exception;

    protected abstract void setDo(X object, W value, T target);

    protected abstract List<T> getRelsByObjectId(X object) throws Exception;

    protected abstract List<T> getRelsByObjectId(Long[] objectIds) throws Exception;

    protected abstract Integer[] getStatusByObjectId(X object) throws Exception;

    protected abstract Map<Long, Integer[]> getStatusByObjectId(Long[] objectIds) throws Exception;

    protected abstract void insert(T[] values) throws Exception;

    protected abstract void updateByPrimaryKey(T[] values) throws Exception;

    protected abstract void deleteByPrimaryKey(T[] values) throws Exception;
}
