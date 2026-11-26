package com.momna.platform.ai;

/**
 * A registered prompt may carry a system part and a user part in one template, separated by
 * {@link #USER_DELIMITER}. The gateway renders the whole template; a chat provider sends the two parts
 * as its system and user messages. A template without the delimiter is one user message.
 */
public final class AiPromptSections {
    public static final String USER_DELIMITER = "\n<<<momna:user>>>\n";

    private AiPromptSections() {}

    public record Sections(String system, String user) {}

    public static String join(String system, String user) {
        return system + USER_DELIMITER + user;
    }

    public static Sections split(String rendered) {
        var at = rendered.indexOf(USER_DELIMITER);
        if (at < 0) return new Sections(null, rendered);
        if (rendered.indexOf(USER_DELIMITER, at + 1) >= 0) {
            throw new IllegalArgumentException("A prompt has one user part");
        }
        return new Sections(rendered.substring(0, at), rendered.substring(at + USER_DELIMITER.length()));
    }
}
