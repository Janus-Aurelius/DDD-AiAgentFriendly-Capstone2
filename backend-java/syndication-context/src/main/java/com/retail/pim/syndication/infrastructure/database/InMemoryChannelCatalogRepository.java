package com.retail.pim.syndication.infrastructure.database;

import com.retail.pim.syndication.domain.model.ChannelCatalog;
import com.retail.pim.syndication.domain.repository.ChannelCatalogRepository;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class InMemoryChannelCatalogRepository implements ChannelCatalogRepository {

    private final Map<UUID, ChannelCatalog> storage = new ConcurrentHashMap<>();

    @Override
    public ChannelCatalog save(ChannelCatalog channel) {
        storage.put(channel.getId(), channel);
        return channel;
    }

    @Override
    public Optional<ChannelCatalog> findById(UUID id) {
        return Optional.ofNullable(storage.get(id));
    }

    @Override
    public Optional<ChannelCatalog> findByChannelCode(String channelCode) {
        return storage.values().stream()
                .filter(c -> c.getChannelCode().equalsIgnoreCase(channelCode))
                .findFirst();
    }

    @Override
    public List<ChannelCatalog> findAllActive() {
        return storage.values().stream()
                .filter(ChannelCatalog::isActive)
                .toList();
    }
}
