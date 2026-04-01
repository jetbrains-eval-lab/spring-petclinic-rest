package org.springframework.samples.petclinic.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.springframework.samples.petclinic.model.Vet;
import org.springframework.samples.petclinic.rest.dto.VetDto;
import org.springframework.samples.petclinic.rest.dto.VetFieldsDto;

import java.util.Collection;

/**
 * Map Vet & VetoDto using mapstruct
 */
@Mapper(uses = SpecialtyMapper.class)
public interface VetMapper {
    @Mapping(target = "username", ignore = true)
    Vet toVet(VetDto vetDto);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "username", ignore = true)
    Vet toVet(VetFieldsDto vetFieldsDto);

    VetDto toVetDto(Vet vet);

    Collection<VetDto> toVetDtos(Collection<Vet> vets);
}
