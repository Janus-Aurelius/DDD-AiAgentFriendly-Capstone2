package com.retail.pim.syndication.domain.repository;

import com.retail.pim.syndication.domain.model.ChannelCatalog;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ChannelCatalogRepository {

    ChannelCatalog save(ChannelCatalog channel);

    Optional<ChannelCatalog> findById(UUID id);

    Optional<ChannelCatalog> findByChannelCode(String channelCode);

    List<ChannelCatalog> findAllActive();
}
