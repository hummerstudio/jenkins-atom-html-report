package atom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jenkinschina.atom.sdk.testing.Atom;
import com.jenkinschina.atom.sdk.testing.AtomResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The html atom (htmlpublisher's core), started the way the runner starts it.
 *
 * <p>These are the checks {@code tools/smoke.sh} made: the report declaration it produces, the two refusals, and the
 * lenient mode where the entry file may be missing.
 */
class HtmlIT {

    private Atom atom;

    @BeforeEach
    void setUp() throws Exception {
        atom = Atom.of();
    }

    @Test
    @DisplayName("a directory with a nested entry point is declared as a report")
    void declaresTheReport() throws Exception {
        AtomResult run = atom.file("out/coverage/index.html", "<html><body>COVERAGE-REPORT</body></html>\n")
                .file("out/coverage/assets/a.css", "x\n")
                .input("{\"path\": \"out/coverage\", \"index\": \"index.html\", \"label\": \"Coverage\","
                        + " \"requireIndex\": \"true\"}")
                .run();

        run.assertOk();
        assertEquals("report", run.field("report.type"), run.file("output.json"));
        assertEquals(run.work().resolve("out/coverage").normalize().toString(), run.field("report.path"));
        assertEquals("index.html", run.field("report.target"), "the entry file is declared");
        assertEquals("Coverage", run.field("report.label"));
        assertTrue(run.console().contains("2 files"), "the file count is reported:\n" + run.console());
    }

    @Test
    @DisplayName("a missing directory is named, not a stack trace")
    void namesTheMissingDirectory() throws Exception {
        AtomResult run = atom.input("{\"path\": \"out/nope\", \"index\": \"index.html\", \"label\": \"Coverage\","
                + " \"requireIndex\": \"true\"}")
                .run();

        run.assertRefused();
        assertTrue(run.file("output.json").contains("report directory not found: out/nope"), run.file("output.json"));
    }

    @Test
    @DisplayName("a directory without the entry point is declared when requireIndex is false")
    void lenientWhenIndexIsNotRequired() throws Exception {
        AtomResult run = atom.input("{\"path\": \"out/empty\", \"index\": \"index.html\", \"label\": \"Empty\","
                + " \"requireIndex\": \"false\"}")
                .file("out/empty/.keep", "")
                .run();

        run.assertOk();
        assertEquals("index.html", run.field("report.target"), "declaration missing in lenient mode");
    }

    @Test
    @DisplayName("escaping the workspace is refused")
    void refusesToEscapeTheWorkspace() throws Exception {
        AtomResult run = atom.input("{\"path\": \"../etc\", \"index\": \"index.html\", \"label\": \"Coverage\","
                + " \"requireIndex\": \"true\"}")
                .run();

        run.assertRefused();
        assertTrue(run.file("output.json").contains("must stay inside the workspace"), run.file("output.json"));
    }
}
