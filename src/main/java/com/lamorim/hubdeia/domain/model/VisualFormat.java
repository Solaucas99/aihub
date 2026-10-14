package com.lamorim.hubdeia.domain.model;

/** How a {@link Visual} is displayed. The core only knows how to show these formats, never who produced them. */
public enum VisualFormat {
	/** Self-contained, sanitized-on-display HTML (for example a table). */
	HTML,
	/** A Vega-Lite specification, drawn by the interface. */
	VEGA_LITE
}
