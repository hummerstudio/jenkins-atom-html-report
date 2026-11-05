package com.jenkinschina.atom.html;

import com.jenkinschina.atom.sdk.AtomContext;
import com.jenkinschina.atom.sdk.AtomResult;
import com.jenkinschina.atom.sdk.AtomTask;
import com.jenkinschina.atom.sdk.ErrorType;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * htmlpublisher as an atom, written against the SDK: declare an existing report directory as a build report.
 *
 * <p>Nothing is rendered here. The runner archives the directory and embeds the entry file in the build page — that is
 * the plugin's "publish HTML reports". The atom only decides which directory, and checks that it is really inside the
 * workspace and really has an entry point.
 */
public class HtmlAtom implements AtomTask {

    @Override
    public AtomResult run(AtomContext context) throws Exception {
        String relative = context.getParam("path", "").trim();
        String index = context.getParam("index", "index.html").trim();
        String label = context.getParam("label", "HTML report").trim();
        boolean requireIndex = context.getBooleanParam("requireIndex", true);
        Path workspace = context.getWorkspace();

        if (relative.isEmpty()) {
            return AtomResult.error(ErrorType.USER, 40001, "path is required (a directory in the workspace)");
        }
        Path root = workspace.resolve(relative).normalize();
        if (!root.startsWith(workspace)) {
            return AtomResult.error(ErrorType.USER, 40002, "path must stay inside the workspace: " + relative);
        }
        if (!Files.isDirectory(root)) {
            return AtomResult.error(
                    ErrorType.USER,
                    40003,
                    "report directory not found: " + relative + " (write it under the workspace before publishing)");
        }
        if (index.contains("..") || index.startsWith("/")) {
            return AtomResult.error(
                    ErrorType.USER, 40004, "index must be a plain file name inside the report directory: " + index);
        }
        if (requireIndex && !Files.isRegularFile(root.resolve(index))) {
            return AtomResult.error(ErrorType.USER, 40005, "entry file not found: " + relative + "/" + index);
        }

        long files;
        try (var walk = Files.walk(root)) {
            files = walk.filter(Files::isRegularFile).count();
        }
        context.log("[html] " + label + ": " + root + " (" + files + " files, entry " + index + ")");
        return AtomResult.success().report("report", label, root.toString(), index);
    }
}
