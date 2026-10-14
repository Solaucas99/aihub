package com.lamorim.hubdeia.application.provider.genie;

import java.util.List;
import java.util.Map;

import com.lamorim.hubdeia.domain.model.Visual;

/** A Genie answer split into what the core understands (text + neutral visuals) and Genie's own details. */
public record GenieParsedResponse(Map<String, Object> structured, List<Visual> visuals) {
}
