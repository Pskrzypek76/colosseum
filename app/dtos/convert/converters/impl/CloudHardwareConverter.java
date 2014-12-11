package dtos.convert.converters.impl;

import com.google.inject.Inject;
import dtos.CloudHardwareDto;
import dtos.builders.CloudHardwareDtoBuilder;
import dtos.convert.converters.api.ModelDtoConverter;
import models.Cloud;
import models.CloudHardware;
import models.Hardware;
import models.service.impl.CloudService;
import models.service.impl.HardwareService;

import static com.google.common.base.Preconditions.checkState;
import static com.google.inject.internal.util.$Preconditions.checkNotNull;

/**
 * Created by daniel seybold on 09.12.2014.
 */
public class CloudHardwareConverter implements ModelDtoConverter<CloudHardware, CloudHardwareDto> {

    private final CloudService cloudService;
    private final HardwareService hardwareService;

    @Inject
    CloudHardwareConverter(CloudService cloudService, HardwareService hardwareService){

        checkNotNull(cloudService);
        checkNotNull(hardwareService);


        this.cloudService = cloudService;
        this.hardwareService = hardwareService;
    }

    protected CloudHardware setDto(CloudHardware cloudHardware, CloudHardwareDto cloudHardwareDto){
        cloudHardware.setUuid(cloudHardwareDto.uuid);

        Cloud cloud = cloudService.getById(cloudHardwareDto.cloud);
        checkState(cloud != null, "Could not retrieve cloud for id: " + cloudHardwareDto.cloud);
        cloudHardware.setCloud(cloud);

        Hardware hardware = hardwareService.getById(cloudHardwareDto.hardware);
        checkState(hardware != null, "Could not retrieve hardware for id: " + cloudHardwareDto.hardware);
        cloudHardware.setHardware(hardware);

        return  cloudHardware;
    }

    @Override
    public CloudHardware toModel(CloudHardwareDto dto) {
        checkNotNull(dto);
        return this.setDto(new CloudHardware(), dto);
    }

    @Override
    public CloudHardware toModel(CloudHardwareDto dto, CloudHardware model) {
        checkNotNull(dto);
        checkNotNull(model);
        return this.setDto(model, dto);
    }

    @Override
    public CloudHardwareDto toDto(CloudHardware model) {
        checkNotNull(model);
        return new CloudHardwareDtoBuilder()
                .uuid(model.getUuid())
                .cloud(model.getCloud().getId())
                .hardware(model.getHardware().getId())
                .build();
    }
}