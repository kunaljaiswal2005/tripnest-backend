package com.tripnest.backend.dto;

import com.tripnest.backend.entity.Destination;
import lombok.Data;

@Data
public class DestinationResponse {

    private Long id;
    private String name;
    private String country;
    private String description;
    private String bestTimeToVisit;
    private Boolean isPopular;

    public static DestinationResponse fromEntity(Destination d) {
        DestinationResponse res = new DestinationResponse();
        res.setId(d.getId());
        res.setName(d.getName());
        res.setCountry(d.getCountry());
        res.setDescription(d.getDescription());
        res.setBestTimeToVisit(d.getBestTimeToVisit());
        res.setIsPopular(d.getIsPopular());
        return res;
    }
}