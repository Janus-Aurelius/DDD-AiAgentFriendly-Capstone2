package com.retail.pim.syndication.domain.service;

import com.retail.pim.syndication.domain.valueobjects.MappingTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Domain Service Registry: Dynamically decouples channel mapping templates per category taxonomy.
 * Resolves distinct character limits, HTML sanitization, and bullet point rules for Electronics vs. Apparel vs. General.
 */
@Component
public class CategoryMappingTemplateRegistry {

    private final Map<String, MappingTemplate> templates = new ConcurrentHashMap<>();

    public CategoryMappingTemplateRegistry() {
        // Amazon Category-Specific Mapping Defaults
        templates.put("AMAZON:ELECTRONICS", new MappingTemplate("Amazon_Consumer_Electronics", 150, true, true));
        templates.put("AMAZON:APPAREL", new MappingTemplate("Amazon_Apparel_Clothing", 200, true, true));
        templates.put("AMAZON:GENERAL", MappingTemplate.forAmazon());

        // Mobile Storefront Defaults
        templates.put("MOBILE:GENERAL", MappingTemplate.forMobileApp());
    }

    public MappingTemplate resolveTemplate(String channelName, String categoryPath) {
        if (channelName == null) channelName = "AMAZON";
        String normalizedChannel = channelName.toUpperCase();

        if (categoryPath != null) {
            String upperCategory = categoryPath.toUpperCase();
            if (upperCategory.contains("ELECTRONICS") || upperCategory.contains("AUDIO") || upperCategory.contains("TV")) {
                MappingTemplate tmpl = templates.get(normalizedChannel + ":ELECTRONICS");
                if (tmpl != null) return tmpl;
            } else if (upperCategory.contains("APPAREL") || upperCategory.contains("CLOTHING") || upperCategory.contains("SHOES")) {
                MappingTemplate tmpl = templates.get(normalizedChannel + ":APPAREL");
                if (tmpl != null) return tmpl;
            }
        }

        return templates.getOrDefault(normalizedChannel + ":GENERAL", MappingTemplate.forAmazon());
    }

    public void registerTemplate(String channel, String categoryKey, MappingTemplate template) {
        templates.put(channel.toUpperCase() + ":" + categoryKey.toUpperCase(), template);
    }
}
