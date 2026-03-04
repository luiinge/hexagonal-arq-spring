package com.example.items.app.mapper;

import com.example.items.app.dto.ItemDto;
import com.example.items.domain.model.Item;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ItemDtoMapper {

	ItemDto toDto(Item item);

}
