/*
 * Copyright 2017 EPAM Systems
 *
 *
 * This file is part of EPAM Report Portal.
 * https://github.com/reportportal/service-api
 *
 * Report Portal is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Report Portal is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Report Portal.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.epam.ta.reportportal.core.imprt.impl;

import com.epam.ta.reportportal.core.imprt.impl.junit.AsyncJunitImportStrategy;
import com.google.common.collect.ImmutableMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class ImportStrategyFactoryImpl implements ImportStrategyFactory {

    private final Map<ImportType, ImportStrategy> MAPPING;

    @Autowired
    public ImportStrategyFactoryImpl(AsyncJunitImportStrategy asyncJunitImportStrategy) {
        MAPPING = ImmutableMap.<ImportType, ImportStrategy>builder()
                .put(ImportType.JUNIT, asyncJunitImportStrategy).build();
    }

    @Override
    public ImportStrategy getImportLaunch(ImportType type) {
        return MAPPING.get(type);
    }
}
