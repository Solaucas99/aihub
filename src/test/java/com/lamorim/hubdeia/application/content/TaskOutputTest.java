package com.lamorim.hubdeia.application.content;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.lamorim.hubdeia.application.ConversationMemorySanitizer;
import com.lamorim.hubdeia.domain.model.Visual;
import com.lamorim.hubdeia.domain.model.VisualFormat;

class TaskOutputTest {

	private final Visual table = new Visual("v1", "Desempenho", VisualFormat.HTML, "<section class=\"data-table\"><table></table></section>");
	private final Visual chart = new Visual("v2", "Receita", VisualFormat.VEGA_LITE, "{\"mark\":\"bar\"}");

	@Test
	void markdownPutsEachVisualWhereItsMarkerIsAndAppendsTheOnesTheTextLeftOut() {
		TaskOutput output = new TaskOutput("Analise [[visualization:v1]] e de novo [[visualization:v1]].", List.of(table, chart));

		assertThat(output.markdown()).isEqualTo("Analise " + TaskOutput.VISUAL_START + "\n\n" + table.payload() + "\n\n" + TaskOutput.VISUAL_END
				+ " e de novo .\n\n```vega-lite\n{\"mark\":\"bar\"}\n```");
	}

	@Test
	void markerWithoutAVisualDisappears() {
		assertThat(new TaskOutput("Veja [[visualization:nao-existe]] aqui", List.of()).markdown()).isEqualTo("Veja  aqui");
	}

	@Test
	void plainTextNeverCarriesHtmlOrChartSpecs() {
		TaskOutput output = new TaskOutput("Resumo.\n\n[[visualization:v1]]\n\nMais texto [[visualization:v2]].", List.of(table, chart));

		String text = output.plainText();

		assertThat(text).contains("[visual: Desempenho]").contains("[visual: Receita]").contains("Resumo.").contains("Mais texto");
		assertThat(text).doesNotContain("<section").doesNotContain("vega").doesNotContain("[[visualization");
	}

	@Test
	void visualsNotReferencedByTheTextAreNotInThePlainText() {
		assertThat(new TaskOutput("So texto.", List.of(table)).plainText()).isEqualTo("So texto.");
	}

	@Test
	void roundTripsThroughTheMapStoredInTasks() {
		TaskOutput original = new TaskOutput("Texto [[visualization:v2]]", List.of(table, chart));

		TaskOutput restored = TaskOutput.from(original.toMap());

		assertThat(restored).isEqualTo(original);
	}

	@Test
	void outputsWithoutVisualsOrWithUnknownFormatsAreStillReadable() {
		assertThat(TaskOutput.from(Map.of("content", "so texto"))).isEqualTo(new TaskOutput("so texto", List.of()));
		assertThat(TaskOutput.from(null).content()).isEmpty();
		TaskOutput odd = TaskOutput.from(Map.of("content", "x", "visuals", List.of(
				Map.of("id", "a", "title", "t", "format", "HOLOGRAM", "payload", "?"),
				Map.of("id", "b", "format", "HTML", "payload", ""),
				Map.of("id", "c", "title", "ok", "format", "HTML", "payload", "<b>ok</b>"))));
		assertThat(odd.visuals()).extracting(Visual::id).containsExactly("c");
	}

	@Test
	void memorySanitizerDropsAssembledVisualsFromWhatModelsRead() {
		String markdown = new TaskOutput("Texto [[visualization:v1]] e [[visualization:v2]] fim", List.of(table, chart)).markdown();

		String sanitized = new ConversationMemorySanitizer().sanitizeForAgent(markdown);

		assertThat(sanitized).isEqualTo("Texto [visual omitido da memoria] e [visual omitido da memoria] fim");
	}
}
