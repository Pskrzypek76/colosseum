package cloud.sync.watchdogs;

import cloud.util.CloudScopedId;
import cloud.HardwareInCloudAndLocation;
import cloud.sync.AbstractCloudServiceWatchdog;
import cloud.sync.Problem;
import cloud.sync.problems.HardwareProblems;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import components.execution.SimpleBlockingQueue;
import de.uniulm.omi.cloudiator.sword.api.domain.HardwareFlavor;
import de.uniulm.omi.cloudiator.sword.api.service.ComputeService;
import models.CloudCredential;
import models.Hardware;
import models.Location;
import models.service.api.HardwareModelService;

/**
 * Created by daniel on 07.05.15.
 */
public class HardwareWatchdog extends AbstractCloudServiceWatchdog {

    private final HardwareModelService hardwareModelService;

    @Inject protected HardwareWatchdog(ComputeService computeService,
        @Named(value = "problemQueue") SimpleBlockingQueue<Problem> simpleBlockingQueue,
        HardwareModelService hardwareModelService) {
        super(computeService, simpleBlockingQueue);
        this.hardwareModelService = hardwareModelService;
    }



    @Override protected void watch(ComputeService computeService) {
        for (HardwareFlavor hardwareFlavor : computeService.listHardwareFlavors()) {
            if (hardwareFlavor instanceof HardwareInCloudAndLocation) {
                final CloudScopedId cloudScopedId =
                    CloudScopedId.of(hardwareFlavor.id());

                Hardware modelHardware = hardwareModelService
                    .getByUuidInCloudAndUuidOfCloudAndUuidOfLocation(
                        cloudScopedId.baseId(), cloudScopedId.cloud());

                if (modelHardware == null) {
                    report(new HardwareProblems.HardwareNotInDatabase(
                        (HardwareInCloudAndLocation) hardwareFlavor));
                } else {
                    CloudCredential credentialToSearchFor = null;
                    for (CloudCredential cloudCredential : modelHardware.getCloudCredentials()) {
                        if (cloudCredential.getUuid()
                            .equals(cloudScopedId.credential())) {
                            credentialToSearchFor = cloudCredential;
                            break;
                        }
                    }

                    if (credentialToSearchFor == null) {
                        report(new HardwareProblems.HardwareMissesCredential(
                            (HardwareInCloudAndLocation) hardwareFlavor));
                    }

                    Location locationToSearchFor = null;
                    for (Location location : modelHardware.getLocations()) {
                        if (location.getCloud().getUuid().equals(cloudScopedId.cloud())
                            && location.getCloudUuid()
                            .equals(cloudScopedId.location())) {
                            locationToSearchFor = location;
                            break;
                        }
                    }

                    if (locationToSearchFor == null) {
                        report(new HardwareProblems.HardwareMissesLocation(
                            (HardwareInCloudAndLocation) hardwareFlavor));
                    }
                }


            }
        }
    }
}
