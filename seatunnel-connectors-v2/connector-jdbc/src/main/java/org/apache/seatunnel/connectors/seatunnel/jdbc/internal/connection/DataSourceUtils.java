/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.seatunnel.connectors.seatunnel.jdbc.internal.connection;

import org.apache.seatunnel.connectors.seatunnel.jdbc.internal.options.JdbcConnectionOptions;

import com.google.common.base.CaseFormat;
import lombok.NonNull;

import javax.sql.CommonDataSource;
import javax.sql.DataSource;

import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class DataSourceUtils
    implements Serializable {
    private static final String GETTER_PREFIX = "get";

    private static final String SETTER_PREFIX = "set";

    public static CommonDataSource buildCommonDataSource(@NonNull JdbcConnectionOptions jdbcConnectionOptions)
        throws InvocationTargetException, IllegalAccessException {
        CommonDataSource dataSource = (CommonDataSource) loadDataSource(jdbcConnectionOptions.getXaDataSourceClassName());
        setProperties(dataSource, buildDatabaseAccessConfig(jdbcConnectionOptions));
        return dataSource;
    }

    private static Map<String, Object> buildDatabaseAccessConfig(JdbcConnectionOptions jdbcConnectionOptions) {
        HashMap<String, Object> accessConfig = new HashMap<>();
        accessConfig.put("url", jdbcConnectionOptions.getUrl());
        if (jdbcConnectionOptions.getUsername().isPresent()) {
            accessConfig.put("user", jdbcConnectionOptions.getUsername().get());
        }
        if (jdbcConnectionOptions.getPassword().isPresent()) {
            accessConfig.put("password", jdbcConnectionOptions.getPassword().get());
        }

        return accessConfig;
    }

    private static void setProperties(final CommonDataSource commonDataSource, final Map<String, Object> databaseAccessConfig)
        throws InvocationTargetException, IllegalAccessException {
        for (Map.Entry<String, Object> entry : databaseAccessConfig.entrySet()) {
            Optional<Method> method = findSetterMethod(commonDataSource.getClass().getMethods(), entry.getKey());
            if (method.isPresent()) {
                method.get().invoke(commonDataSource, entry.getValue());
            }
        }
    }

    private static Method findGetterMethod(final DataSource dataSource, final String propertyName)
        throws NoSuchMethodException {
        String getterMethodName = GETTER_PREFIX + CaseFormat.LOWER_CAMEL.to(CaseFormat.UPPER_CAMEL, propertyName);
        Method result = dataSource.getClass().getMethod(getterMethodName);
        result.setAccessible(true);
        return result;
    }

    private static Optional<Method> findSetterMethod(final Method[] methods, final String property) {
        String setterMethodName = SETTER_PREFIX + CaseFormat.LOWER_CAMEL.to(CaseFormat.UPPER_CAMEL, property);
        return Arrays.stream(methods)
            .filter(each -> each.getName().equals(setterMethodName) && 1 == each.getParameterTypes().length)
            .findFirst();
    }

    private static Object loadDataSource(final String xaDataSourceClassName) {
        Class<?> xaDataSourceClass;
        try {
            xaDataSourceClass = Thread.currentThread().getContextClassLoader().loadClass(xaDataSourceClassName);
        } catch (final ClassNotFoundException ignored) {
            try {
                xaDataSourceClass = Class.forName(xaDataSourceClassName);
            } catch (final ClassNotFoundException ex) {
                throw new RuntimeException("Failed to load [" + xaDataSourceClassName + "]", ex);
            }
        }
        try {
            return xaDataSourceClass.getDeclaredConstructor().newInstance();
        } catch (final ReflectiveOperationException ex) {
            throw new RuntimeException("Failed to instance [" + xaDataSourceClassName + "]", ex);
        }
    }
}
