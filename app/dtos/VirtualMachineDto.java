package dtos;

import com.google.inject.Inject;
import com.google.inject.Provider;
import dtos.generic.impl.NamedDto;
import models.Cloud;
import models.CloudHardware;
import models.CloudImage;
import models.CloudLocation;
import models.service.api.CloudHardwareFlavorServiceInterface;
import models.service.api.CloudImageServiceInterface;
import models.service.api.CloudLocationServiceInterface;
import models.service.api.CloudServiceInterface;
import play.data.validation.ValidationError;

import java.util.List;

/**
 * Created by bwpc on 09.12.2014.
 */
public class VirtualMachineDto extends NamedDto {

    public static class References {
        @Inject
        public static Provider<CloudServiceInterface> cloudService;
        @Inject
        public static Provider<CloudImageServiceInterface> cloudImageService;
        @Inject
        public static Provider<CloudHardwareFlavorServiceInterface> cloudHardwareFlavorService;
        @Inject
        public static Provider<CloudLocationServiceInterface> cloudLocationService;
    }

    public Long cloud;

    public Long cloudImage;

    public Long cloudHardware;

    public Long cloudLocation;

    public VirtualMachineDto() {
    }

    public VirtualMachineDto(String name, Long cloud, Long cloudImage, Long cloudHardware, Long cloudLocation) {
        super(name);
        this.cloud = cloud;
        this.cloudImage = cloudImage;
        this.cloudHardware = cloudHardware;
        this.cloudLocation = cloudLocation;
    }

    @Override
    public List<ValidationError> validateNotNull() {
        final List<ValidationError> errors = super.validateNotNull();

        //validate cloud reference
        Cloud cloud = null;
        if (this.cloud == null) {
            errors.add(new ValidationError("cloud", "The cloud is required."));
        } else {
            cloud = References.cloudService.get().getById(this.cloud);
            if (cloud == null) {
                errors.add(new ValidationError("cloud", "The given cloud is invalid."));
            }
        }

        //validate the cloud image
        if (this.cloudImage == null) {
            errors.add(new ValidationError("cloudImage", "The cloud image is required."));
        } else {
            final CloudImage cloudImage = References.cloudImageService.get().getById(this.cloudImage);
            if (cloudImage == null) {
                errors.add(new ValidationError("cloudImage", "The given cloud image is invalid."));
            } else {
                if (!cloudImage.getCloud().equals(cloud)) {
                    errors.add(new ValidationError("cloudImage", "The cloud image does not belong to the specified cloud."));
                }
            }
        }

        //validate the cloud hardware
        if (this.cloudHardware == null) {
            errors.add(new ValidationError("cloudHardware", "The cloud hardware is required."));
        } else {
            final CloudHardware cloudHardware = References.cloudHardwareFlavorService.get().getById(this.cloudHardware);
            if (cloudHardware == null) {
                errors.add(new ValidationError("cloudHardware", "The given cloud hardware is invalid"));
            } else {
                if (!cloudHardware.getCloud().equals(cloud)) {
                    errors.add(new ValidationError("cloudHardware", "The cloud hardware does not belong to the specified cloud."));
                }
            }
        }

        //validate the cloud location
        if (this.cloudLocation == null) {
            errors.add(new ValidationError("cloudLocation", "The cloud location is required."));
        } else {
            final CloudLocation cloudLocation = References.cloudLocationService.get().getById(this.cloudLocation);
            if (cloudLocation == null) {
                errors.add(new ValidationError("cloudLocation", "The given cloud location is invalid."));
            } else {
                if (!cloudLocation.getCloud().equals(cloud)) {
                    errors.add(new ValidationError("cloudLocation", "The cloud location does not belong to the specified cloud."));
                }
            }
        }

        return errors;
    }
}