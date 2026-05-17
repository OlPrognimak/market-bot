package com.prognimak.marketbot.mapper;

import com.prognimak.marketbot.entity.QuoteEntity;
import com.prognimak.marketbot.model.Quote;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface QuoteMapper {

    Quote toQuote(QuoteEntity entity);
    List<Quote> toQuotes(List<QuoteEntity> entities);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "send", ignore = true)
    QuoteEntity toEntity(Quote quote);
}
