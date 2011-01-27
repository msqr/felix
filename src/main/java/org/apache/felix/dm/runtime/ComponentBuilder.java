/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.felix.dm.runtime;

import java.util.Dictionary;
import java.util.Hashtable;
import java.util.List;
import java.util.Set;

import org.apache.felix.dm.Component;
import org.apache.felix.dm.DependencyManager;
import org.osgi.framework.Bundle;

/**
 * Builds a DependencyManager Component.
 */
public class ComponentBuilder extends AbstractBuilder
{
    private final static String TYPE = "Component";
    private final static String DM_FACTORY_NAME = "dm.factory.name";

    @Override
    public String getType()
    {
        return TYPE;
    }

    @Override
    public void build(MetaData srvMeta, List<MetaData> depsMeta, Bundle b, DependencyManager dm)
        throws Exception
    {
        Component service = dm.createComponent();
        String factory = srvMeta.getString(Params.factorySet, null);

        // Check if we must provide a Set Factory.
        if (factory == null)
        {
            Log.instance().info("ComponentBuilder: building service %s with dependencies %s",
                                srvMeta,
                                depsMeta);

            String impl = srvMeta.getString(Params.impl);
            String composition = srvMeta.getString(Params.composition, null);
            String factoryMethod = srvMeta.getString(Params.factoryMethod, null);
            if (factoryMethod == null)
            {
                service.setImplementation(b.loadClass(impl));
            }
            else
            {
                service.setFactory(b.loadClass(impl), factoryMethod);
            }
            service.setComposition(composition);

            // Adds dependencies (except named dependencies, which are managed by the lifecycle
            // handler).
            addUnamedDependencies(b, dm, service, srvMeta, depsMeta);
            // Creates a ServiceHandler, which will filter all service lifecycle callbacks.
            ServiceLifecycleHandler lfcleHandler =
                    new ServiceLifecycleHandler(service, b, dm, srvMeta, depsMeta);
            service.setCallbacks(lfcleHandler, "init", "start", "stop", "destroy");

            // Set the provided services
            Dictionary<String, Object> properties = srvMeta.getDictionary(Params.properties, null);
            String[] services = srvMeta.getStrings(Params.provides, null);
            service.setInterface(services, properties);
        }
        else
        {
            Log.instance()
                    .info("ComponentBuilder: providing factory set for service %s with dependencies %s",
                          srvMeta,
                          depsMeta);

            // We don't instantiate the service, but instead we provide a Set in the registry.
            // This Set will act as a factory and another component may registers some
            // service configurations into it in order to fire some service instantiations.
            FactorySet factorySet = new FactorySet(b, srvMeta, depsMeta);
            service.setImplementation(factorySet);
            service.setCallbacks(null, "start", "stop", null);
            Hashtable<String, String> props = new Hashtable<String, String>();
            props.put(DM_FACTORY_NAME, factory);
            service.setInterface(Set.class.getName(), props);
        }

        dm.add(service);
    }
}
