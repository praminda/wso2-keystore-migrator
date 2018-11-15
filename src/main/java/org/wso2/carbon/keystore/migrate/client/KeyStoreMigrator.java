/*
 * Copyright (c) 2018, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.wso2.carbon.keystore.migrate.client;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.context.PrivilegedCarbonContext;
import org.wso2.carbon.keystore.mgt.KeyStoreGenerator;
import org.wso2.carbon.keystore.mgt.KeyStoreMgtException;
import org.wso2.carbon.keystore.mgt.util.RegistryServiceHolder;
import org.wso2.carbon.keystore.migrate.client.internal.ServiceHolder;
import org.wso2.carbon.registry.core.exceptions.RegistryException;
import org.wso2.carbon.registry.core.service.TenantRegistryLoader;
import org.wso2.carbon.registry.core.session.UserRegistry;
import org.wso2.carbon.user.api.Tenant;
import org.wso2.carbon.user.api.UserStoreException;
import org.wso2.carbon.user.core.tenant.TenantManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Migration implementation for WSO2 Carbon tenant key stores.
 * This implementation will remove all existing tenant registry key stores,
 * public certificates and create new resources for the same.
 */

@SuppressWarnings("unchecked")
public class KeyStoreMigrator {
    private static final Log log = LogFactory.getLog(KeyStoreMigrator.class);

    public void migrate() throws UserStoreException {
        TenantManager tenantManager = ServiceHolder.getRealmService().getTenantManager();
        List<Tenant> allTenants = new ArrayList(Arrays.asList(tenantManager.getAllTenants()));
        boolean isTenantFlowStarted = false;
        String tenantDomain;
        String ksName;

        for (Tenant tenant : allTenants) {
            try {
                tenantDomain = tenant.getDomain();
                ksName = getKSNameFromDomainName(tenant.getDomain());
                isTenantFlowStarted = true;
                PrivilegedCarbonContext.startTenantFlow();
                PrivilegedCarbonContext.getThreadLocalCarbonContext().setTenantDomain(tenantDomain, true);
                TenantRegistryLoader tenantRegLoaderl = ServiceHolder.getTenantRegLoader();
                tenantRegLoaderl.loadTenantRegistry(tenant.getId());

                // can't add new keystores when there are existing ones in the same location
                // so first we delete the old resources
                removeExistingCerts(tenant.getId());
                removeExistingKeyStore(ksName, tenant.getId());

                // generate a new key store and public certificate
                KeyStoreGenerator ksGen = new KeyStoreGenerator(tenant.getId());
                ksGen.generateKeyStore();
                log.info("********Key store migration successfully completed for tenant: [" + tenant.getId() + ']'
                        + tenant.getDomain());
            } catch (KeyStoreMgtException e) {
                log.error(
                        "****####Failed to generate keystore for tenant: [" + tenant.getId() + ']' + tenant.getDomain(),
                        e);
            } catch (RegistryException e) {
                log.error("****####Failed to remove existing keystore for tenant: [" + tenant.getId() + ']' + tenant
                        .getDomain(), e);
            } catch (Exception e) {
                log.error(
                        "****####Error occurred while migrating keystore for tenant: [" + tenant.getId() + ']' + tenant
                                .getDomain(), e);
            } finally {
                if (isTenantFlowStarted) {
                    PrivilegedCarbonContext.endTenantFlow();
                }
            }
        }
    }

    private void removeExistingKeyStore(String ksName, int tenantId) throws RegistryException {
        UserRegistry govRegistry = RegistryServiceHolder.getRegistryService().getGovernanceSystemRegistry(tenantId);
        String path = "/repository/security/key-stores/" + ksName;

        if (govRegistry.resourceExists(path)) {
            govRegistry.delete(path);
        }
    }

    private void removeExistingCerts(int tenantId) throws RegistryException {
        UserRegistry govRegistry = RegistryServiceHolder.getRegistryService().getGovernanceSystemRegistry(tenantId);
        String path = "/repository/security/pub-key";

        if (govRegistry.resourceExists(path)) {
            govRegistry.delete(path);
        }
    }

    private String getKSNameFromDomainName(String domain) {
        String ksName = domain.trim().replace(".", "-");
        return ksName + ".jks";
    }
}
