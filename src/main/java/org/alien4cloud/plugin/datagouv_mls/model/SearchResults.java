package org.alien4cloud.plugin.datagouv_mls.model;

import lombok.Getter;
import lombok.Setter;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@Getter
@Setter

@JsonIgnoreProperties(ignoreUnknown = true)
public class SearchResults {
   SearchResult searchResults;
}
