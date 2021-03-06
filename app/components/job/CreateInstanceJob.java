/*
 * Copyright (c) 2014-2015 University of Ulm
 *
 * See the NOTICE file distributed with this work for additional information
 * regarding copyright ownership.  Licensed under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package components.job;

import cloud.colosseum.ColosseumComputeService;
import components.model.ModelValidationService;
import de.uniulm.omi.cloudiator.lance.application.ApplicationId;
import de.uniulm.omi.cloudiator.lance.application.ApplicationInstanceId;
import de.uniulm.omi.cloudiator.lance.application.DeploymentContext;
import de.uniulm.omi.cloudiator.lance.application.component.ComponentId;
import de.uniulm.omi.cloudiator.lance.application.component.DeployableComponent;
import de.uniulm.omi.cloudiator.lance.client.LifecycleClient;
import de.uniulm.omi.cloudiator.lance.container.spec.os.OperatingSystem;
import de.uniulm.omi.cloudiator.lance.lca.DeploymentException;
import de.uniulm.omi.cloudiator.lance.lca.container.ComponentInstanceId;
import de.uniulm.omi.cloudiator.lance.lca.container.ContainerType;
import de.uniulm.omi.cloudiator.lance.lca.registry.RegistrationException;
import deployment.*;
import models.ApplicationComponent;
import models.Instance;
import models.Tenant;
import models.VirtualMachine;
import models.generic.RemoteState;
import models.service.ModelService;
import models.service.RemoteModelService;
import play.Configuration;
import play.Logger;
import play.db.jpa.JPAApi;
import util.ConfigurationConstants;
import util.logging.Loggers;

import java.rmi.NotBoundException;
import java.rmi.RemoteException;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

/**
 * Created by daniel on 03.08.15.
 */
public class CreateInstanceJob extends AbstractRemoteResourceJob<Instance> {

    private Logger.ALogger LOGGER = Loggers.of(Loggers.CLOUD_JOB);

    private final ModelValidationService modelValidationService;
    private final ApplicationToApplicationId applicationToApplicationId;
    private final ApplicationInstanceToApplicationInstanceId
        applicationInstanceToApplicationInstanceId;
    private final ApplicationComponentToComponentId applicationComponentToComponentId;
    private final ApplicationComponentToDeployableComponent
        applicationComponentToDeployableComponent;
    private final ApplicationComponentToContainerType applicationComponentToContainerType;
    private final OsConverter osConverter;
    private final Configuration configuration;

    public CreateInstanceJob(Configuration configuration, JPAApi jpaApi, Instance instance,
        RemoteModelService<Instance> modelService, ModelService<Tenant> tenantModelService,
        ColosseumComputeService colosseumComputeService, Tenant tenant,
        ModelValidationService modelValidationService) {
        super(jpaApi, instance, modelService, tenantModelService, colosseumComputeService, tenant);

        checkNotNull(modelValidationService);
        checkNotNull(configuration);

        this.modelValidationService = modelValidationService;
        applicationToApplicationId = new ApplicationToApplicationId();
        applicationInstanceToApplicationInstanceId =
            new ApplicationInstanceToApplicationInstanceId();
        applicationComponentToComponentId = new ApplicationComponentToComponentId();
        applicationComponentToDeployableComponent = new ApplicationComponentToDeployableComponent();
        applicationComponentToContainerType =
            new ApplicationComponentToContainerType(configuration);
        osConverter = new OsConverter();
        this.configuration = configuration;
    }

    private String getIp() throws JobException {
        String serverIp;
        try {
            serverIp = jpaApi().withTransaction(() -> {
                final Instance instance = getT();
                final VirtualMachine virtualMachine = instance.getVirtualMachine();
                checkState(virtualMachine.publicIpAddress().isPresent(),
                    "virtual machine has no public ip.");
                return virtualMachine.publicIpAddress().get().getIp();
            });
        } catch (Throwable throwable) {
            throw new JobException("Error while retrieving public ip of virtual machine.",
                throwable);
        }
        return serverIp;
    }

    private LifecycleClient getLifecycleClient(String serverIp) throws JobException {
        final LifecycleClient lifecycleClient;
        try {
            lifecycleClient = LifecycleClient
                .getClient(serverIp, configuration.getInt(ConfigurationConstants.RMI_TIMEOUT, 0));
        } catch (RemoteException | NotBoundException e) {
            throw new JobException("Error creating lifecycle client", e);
        }
        return lifecycleClient;
    }

    private ContainerType getContainerType() throws JobException {
        ContainerType containerType;
        try {
            containerType = jpaApi().withTransaction(() -> {
                Instance instance = getT();
                return applicationComponentToContainerType
                    .apply(instance.getApplicationComponent());
            });
        } catch (Throwable throwable) {
            throw new JobException("Error while trying to resolve containerType", throwable);
        }
        return containerType;
    }

    @Override protected void doWork(ModelService<Instance> modelService,
        ColosseumComputeService computeService) throws JobException {

        final String serverIp = getIp();
        final LifecycleClient lifecycleClient = getLifecycleClient(serverIp);

        if (configuration.getBoolean(ConfigurationConstants.MODEL_VALIDATION, true)) {
            //todo: should normally be validated in an application instance method.
            LOGGER.info("Starting validation of model.");
            try {
                jpaApi().withTransaction(() -> modelValidationService
                    .validate(getT().getApplicationComponent().getApplication()));
            } catch (Throwable t) {
                throw new JobException("Error while validation of model", t);
            }
            LOGGER.info("Finished validation of model.");
        }

        //build ApplicationId
        ApplicationId applicationId;
        try {
            applicationId = jpaApi().withTransaction(() -> {
                final Instance instance = getT();
                return applicationToApplicationId
                    .apply(instance.getApplicationInstance().getApplication());
            });
        } catch (Throwable throwable) {
            throw new JobException("Unable to build applicationId", throwable);
        }

        //build applicationInstanceId
        ApplicationInstanceId applicationInstanceId;
        try {
            applicationInstanceId = jpaApi().withTransaction(() -> {
                final Instance instance = getT();
                return applicationInstanceToApplicationInstanceId
                    .apply(instance.getApplicationInstance());
            });
        } catch (Throwable throwable) {
            throw new JobException("Unable to build applicationInstanceId", throwable);
        }

        //register applicationInstance at lifecycle client
        LOGGER.debug(String.format(
            "Registering new applicationInstance %s for application %s at lance using client %s",
            applicationInstanceId, applicationId, lifecycleClient));
        boolean couldRegisterApplicationInstance;
        try {
            couldRegisterApplicationInstance =
                lifecycleClient.registerApplicationInstance(applicationInstanceId, applicationId);
        } catch (RegistrationException e) {
            throw new JobException(
                String.format("Could not register applicationInstance %s.", applicationInstanceId),
                e);
        }

        if (couldRegisterApplicationInstance) {
            registerApplicationComponentsForApplicationInstance(lifecycleClient,
                applicationInstanceId);
        } else {
            LOGGER.debug(String.format(
                "Could not register applicationInstance %s, assuming it was already registered.",
                applicationInstanceId));
        }

        //create the deployment context
        final DeploymentContext deploymentContext =
            lifecycleClient.initDeploymentContext(applicationId, applicationInstanceId);
        LOGGER.debug(String.format("Initialized deployment context %s.", deploymentContext));
        //register the application component at the deployment context
        LOGGER.debug(String.format("Registering application component at deployment context %s.",
            deploymentContext));

        try {
            jpaApi().withTransaction(() -> {
                Instance instance = getT();
                ApplicationComponentDeploymentContextVisitor
                    applicationComponentDeploymentContextVisitor =
                    new ApplicationComponentDeploymentContextVisitor(
                        instance.getApplicationComponent());
                applicationComponentDeploymentContextVisitor
                    .registerAtDeploymentContext(deploymentContext);
            });
        } catch (Throwable t) {
            throw new JobException(String
                .format("Error while registering application component at deployment context %s",
                    deploymentContext), t);
        }

        DeployableComponent deployableComponent;
        try {
            deployableComponent = jpaApi().withTransaction(() -> {

                Instance instance = getT();
                LOGGER.debug(String
                    .format("Creating deployable component for application component %s.",
                        instance.getApplicationComponent()));
                return applicationComponentToDeployableComponent
                    .apply(instance.getApplicationComponent());
            });
        } catch (Throwable throwable) {
            throw new JobException("Error while building deployable component.", throwable);
        }
        LOGGER.debug(
            String.format("Successfully build deployable component %s", deployableComponent));

        final ContainerType containerType = getContainerType();

        OperatingSystem lanceOs;
        try {
            lanceOs = jpaApi().withTransaction(() -> {
                final Instance instance = getT();
                return osConverter.apply(instance.getVirtualMachine().operatingSystem());
            });
        } catch (Throwable throwable) {
            throw new JobException("Error while resolving operating system.", throwable);
        }

        LOGGER.debug(String.format(
            "Calling client %s to deploy instance using: deploymentContext %s, deployableComponent %s, containerType %s.",
            lifecycleClient, deploymentContext, deployableComponent, containerType));

        ComponentInstanceId componentInstanceId;

        try {
            componentInstanceId = lifecycleClient
                .deploy(deploymentContext, deployableComponent, lanceOs, containerType);
        } catch (DeploymentException e) {
            throw new JobException("Error during deployment.", e);
        }

        LOGGER.debug(
            String.format("Client successfully initialized instance %s", componentInstanceId));

        try {
            jpaApi().withTransaction(() -> {
                Instance instance = getT();
                instance.bindRemoteId(componentInstanceId.toString());
                modelService.save(instance);
                LOGGER.debug(String
                    .format("Updated instance %s in database. Set remote ID to %s.", instance,
                        componentInstanceId));
            });
        } catch (Exception e) {
            throw new JobException("Error while updating remote id of instance.", e);
        }

        lifecycleClient.waitForDeployment(componentInstanceId);

        LOGGER.debug(String
            .format("Client deployed the instance with component instance ID %s successfully",
                componentInstanceId));
    }

    private void registerApplicationComponentsForApplicationInstance(
        LifecycleClient lifecycleClient, ApplicationInstanceId applicationInstanceId)
        throws JobException {

        LOGGER.debug(String
            .format("Starting registration of application components for applicationInstance %s.",
                applicationInstanceId));
        try {
            jpaApi().withTransaction(() -> {

                final Instance instance = getT();
                for (ApplicationComponent applicationComponent : instance.getApplicationInstance()
                    .getApplication().getApplicationComponents()) {

                    final ComponentId componentId =
                        applicationComponentToComponentId.apply(applicationComponent);

                    lifecycleClient
                        .registerComponentForApplicationInstance(applicationInstanceId, componentId,
                            applicationComponent.getComponent().getName());
                    LOGGER.debug(String.format(
                        "Registered application component %s as component ID %s for applicationInstance %s.",
                        applicationComponent, componentId, applicationInstanceId));
                }
            });
        } catch (Throwable t) {
            throw new JobException(String.format(
                "Exception occurred while registering application components of applicationInstance %s.",
                applicationInstanceId), t);
        }
    }

    @Override public boolean canStart() throws JobException {
        try {
            return jpaApi().withTransaction(
                () -> RemoteState.OK.equals(getT().getVirtualMachine().getRemoteState()));
        } catch (Throwable throwable) {
            throw new JobException(throwable);
        }
    }

    @Override public void onError() throws JobException {
        final String serverIp = getIp();
        final LifecycleClient lifecycleClient = getLifecycleClient(serverIp);
        final ContainerType containerType = getContainerType();
        if (configuration.getBoolean(ConfigurationConstants.DELETE_FAILED_INSTANCES, false)) {
            jpaApi().withTransaction(() -> {
                Instance instance = getT();

                if (!instance.remoteId().isPresent()) {
                    throw new JobException("no remote id present on instance");
                }

                try {
                    final boolean undeploy = lifecycleClient
                        .undeploy(ComponentInstanceId.fromString(instance.remoteId().get()),
                            containerType);

                    if (!undeploy) {
                        throw new JobException("Undeployment did not work.");
                    }

                } catch (DeploymentException e) {
                    throw new JobException(e);
                }
            });
        }
        super.onError();
    }
}
