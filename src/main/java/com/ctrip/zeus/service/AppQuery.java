package com.ctrip.zeus.service;

import com.ctrip.zeus.model.entity.App;
import com.ctrip.zeus.model.entity.Slb;
import org.unidal.dal.jdbc.DalException;

import java.util.List;

/**
 * @author:xingchaowang
 * @date: 3/7/2015.
 */
public interface AppQuery {
    App get(String name) throws DalException;

    List<App> getAll() throws DalException;
}