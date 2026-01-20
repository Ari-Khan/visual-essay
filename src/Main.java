import javax.swing.*;
import java.awt.*;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import javax.swing.Timer;

public class Main extends JFrame {
    public Main() {
        setTitle("Writer's Block");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(800, 600);
        setLocationRelativeTo(null);
        setResizable(false);
        add(new AnimationPanel());
        setVisible(true);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new Main());
    }
}

class AnimationPanel extends JPanel {
    private int frame = 0;
    private final int TITLE_FADE_IN = 15;
    private final int TITLE_HOLD = 30;
    private final int BYLINE_FADE_IN = 15;
    private final int BYLINE_HOLD = 15;
    private final int FADE_OUT = 15;
    private final int FRAME_FADE_IN = 15;
    
    private String documentTitle = "";
    private String oldTitle = "";  // title fading out
    private String newTitle = "";  // title fading in
    private long titleFadeOutStartNanos = -1;  // when title fade-out started
    private long titleFadeInStartNanos = -1;   // when title fade-in started
    private final long TITLE_FADE_OUT_DURATION = 300_000_000L;  // 300ms fade-out
    private final long TITLE_FADE_IN_DURATION = 300_000_000L;   // 300ms fade-in
    private List<String> titleLines = new ArrayList<>();
    private List<String> leftComments = new ArrayList<>();
    private List<String> rightComments = new ArrayList<>();
    private String currentLeftComment = "";
    private String currentRightComment = "";
    
    // Typing effect state
    private String rawText = "";            // original writing with markers
    private StringBuilder typedBuffer = new StringBuilder(); // currently visible text
    private int rawIndex = 0;                // index into rawText
    private double charAccumulator = 0.0;    // fractional character accumulator
    private long lastTimeNanos = -1;         // last timestamp for typing update
    private double totalTypingSeconds = 0.0; // total typing time since start
    private final double MIN_WPM = 100.0;     // starting speed
    private final double MAX_WPM = 1000.0;    // max speed (increased)
    private final double ACCEL_DURATION = 10.0; // faster acceleration to max speed
    private long pauseUntilNanos = -1;       // typing pause control
    private long leftCommentClearUntilNanos = -1;  // clear left comment at this time
    private long rightCommentClearUntilNanos = -1; // clear right comment at this time
    private long leftCommentStartNanos = -1;  // when left comment fade-in started
    private long rightCommentStartNanos = -1; // when right comment fade-in started
    private final long COMMENT_FADE_DURATION_NANOS = 300_000_000L; // 300ms fade-in
    private final Random rng = new Random(); // for mistake simulation
    private boolean mistakeActive = false;   // whether we are deleting a mistake
    private int mistakeDeleteRemaining = 0;  // characters to delete for mistake
    private long mistakePauseUntilNanos = -1;    // brief pause before fixing mistake
    private long mistakeCooldownUntilNanos = -1; // prevent back-to-back mistakes
    private double clockMinutes = 0.0;  // clock time in minutes since 12:00 (starts at 0 = 12:00)
    
    public AnimationPanel() {
        loadFiles();
        
        Timer timer = new Timer(30, e -> {
            frame++;
            updateTyping();
            repaint();
        });
        timer.start();
    }
    
    private void loadFiles() {
        try {
            String writingPath = "C:\\Users\\ariba\\Desktop\\Program Files\\ICS\\School\\visual-essay\\src\\writing.txt";
            String raw = new String(Files.readAllBytes(Paths.get(writingPath)));
            
            // Load titles.txt
            String titlesPath = "C:\\Users\\ariba\\Desktop\\Program Files\\ICS\\School\\visual-essay\\src\\titles.txt";
            titleLines = Files.readAllLines(Paths.get(titlesPath));
            
            // Load comments
            String leftPath = "C:\\Users\\ariba\\Desktop\\Program Files\\ICS\\School\\visual-essay\\src\\left_comments.txt";
            String rightPath = "C:\\Users\\ariba\\Desktop\\Program Files\\ICS\\School\\visual-essay\\src\\right_comments.txt";
            if (Files.exists(Paths.get(leftPath))) leftComments = Files.readAllLines(Paths.get(leftPath));
            if (Files.exists(Paths.get(rightPath))) rightComments = Files.readAllLines(Paths.get(rightPath));
            
            // Parse [TITLE_NUMBER] pattern
            int start = raw.indexOf("[TITLE_");
            if (start >= 0) {
                int end = raw.indexOf("]", start);
                if (end > start) {
                    // Extract the number after the underscore in "[TITLE_#]"
                    String marker = raw.substring(start + 7, end);
                    try {
                        int lineNum = Integer.parseInt(marker) - 1; // Convert to 0-based
                        if (lineNum >= 0 && lineNum < titleLines.size()) {
                            documentTitle = titleLines.get(lineNum);
                        }
                    } catch (NumberFormatException ex) {
                        documentTitle = "";
                    }
                }
            }

            // Prepare typing stream with markers
            rawText = raw;
            typedBuffer.setLength(0);
            rawIndex = 0;
        } catch (Exception e) {
            documentTitle = "";
            e.printStackTrace();
        }
    }

    private int getFadeOutEndFrame() {
        int titleEnd = TITLE_FADE_IN + TITLE_HOLD;
        int bylineEnd = titleEnd + BYLINE_FADE_IN + BYLINE_HOLD;
        return bylineEnd + FADE_OUT;
    }

    private void updateTyping() {
        // Start typing after the intro fades out and frames are visible
        if (frame <= getFadeOutEndFrame()) {
            lastTimeNanos = -1; // ensure timer starts when typing begins
            return;
        }
        long now = System.nanoTime();
        if (lastTimeNanos < 0) {
            lastTimeNanos = now;
            return;
        }

        if (pauseUntilNanos > 0 && now < pauseUntilNanos) {
            return;
        }

        // Pause ended; reset timing so acceleration starts fresh
        if (pauseUntilNanos > 0 && now >= pauseUntilNanos) {
            pauseUntilNanos = -1;
            lastTimeNanos = now; // critical: prevent dt spike when pause ends
        }

        // Handle comment auto-clear
        if (leftCommentClearUntilNanos > 0 && now >= leftCommentClearUntilNanos) {
            currentLeftComment = "";
            leftCommentClearUntilNanos = -1;
            leftCommentStartNanos = -1;
        }
        if (rightCommentClearUntilNanos > 0 && now >= rightCommentClearUntilNanos) {
            currentRightComment = "";
            rightCommentClearUntilNanos = -1;
            rightCommentStartNanos = -1;
        }

        // If we're fixing a mistake, wait briefly, then delete characters one by one
        if (mistakeActive) {
            if (mistakePauseUntilNanos > 0 && now < mistakePauseUntilNanos) {
                return;
            }
            if (mistakeDeleteRemaining > 0 && typedBuffer.length() > 0) {
                typedBuffer.deleteCharAt(typedBuffer.length() - 1);
                mistakeDeleteRemaining--;
                return;
            } else {
                mistakeActive = false;
                mistakeDeleteRemaining = 0;
                mistakePauseUntilNanos = -1;
                mistakeCooldownUntilNanos = now + 500_000_000L; // 0.5s cooldown before next mistake
            }
        }

        double dt = (now - lastTimeNanos) / 1_000_000_000.0; // seconds
        lastTimeNanos = now;
        totalTypingSeconds += dt;

        // Acceleration from MIN_WPM to MAX_WPM over ACCEL_DURATION seconds
        double t = Math.min(1.0, totalTypingSeconds / ACCEL_DURATION);
        double tEased = t * t;
        double currentWpm = MIN_WPM + (MAX_WPM - MIN_WPM) * tEased;
        double charsPerSecond = (currentWpm * 5.0) / 60.0;
        double charsThisTick = charsPerSecond * dt;

        // Update clock: faster with typing speed, slower during pause/comments
        double clockSpeed = 1.0; // base speed multiplier
        if (pauseUntilNanos > 0 || leftCommentClearUntilNanos > 0 || rightCommentClearUntilNanos > 0) {
            clockSpeed = 0.3; // slow down during pauses/comments
        } else {
            clockSpeed = currentWpm / MIN_WPM; // speed up with typing
        }
        clockMinutes += (dt / 60.0) * clockSpeed * 16.0; // convert seconds to minutes with speed multiplier (8x faster)

        charAccumulator += charsThisTick;
        while (charAccumulator >= 1.0) {
            if (rawIndex >= rawText.length()) break; // finished

            // Marker handling
            if (rawText.charAt(rawIndex) == '[') {
                int close = rawText.indexOf(']', rawIndex);
                if (close > rawIndex) {
                    String tag = rawText.substring(rawIndex + 1, close); // without brackets
                    if (handleMarker(tag, now)) {
                        rawIndex = close + 1; // consume marker
                        // if we started a pause, stop consuming this tick
                        if (pauseUntilNanos > 0 && System.nanoTime() < pauseUntilNanos) {
                            break;
                        }
                        continue; // process next
                    }
                }
            }

            // Normal character with occasional mistake simulation
            char correct = rawText.charAt(rawIndex);

                        boolean triggerMistake = !mistakeActive
                            && (mistakeCooldownUntilNanos < 0 || now >= mistakeCooldownUntilNanos)
                            && !Character.isWhitespace(correct)
                            && rng.nextDouble() < 0.015; // ~1.5% chance per character for realism

            if (triggerMistake) {
                // Decide a wrong character
                char wrong = correct;
                if (Character.isLetter(correct)) {
                    char base = Character.isUpperCase(correct) ? 'A' : 'a';
                    do {
                        wrong = (char) (base + rng.nextInt(26));
                    } while (Character.toLowerCase(wrong) == Character.toLowerCase(correct));
                } else {
                    wrong = (char) ('a' + rng.nextInt(26));
                }

                // Determine how many chars to erase (current word including this wrong char)
                int wordStart = typedBuffer.length();
                for (int i = typedBuffer.length() - 1; i >= 0; i--) {
                    char c = typedBuffer.charAt(i);
                    if (Character.isWhitespace(c)) {
                        wordStart = i + 1;
                        break;
                    }
                }
                int deleteCount = (typedBuffer.length() - wordStart) + 1; // include wrong char we add now

                // Move rawIndex back to the start of the word so it retypes correctly
                int rawWordStart = rawIndex;
                while (rawWordStart > 0 && !Character.isWhitespace(rawText.charAt(rawWordStart - 1))) {
                    rawWordStart--;
                }
                rawIndex = rawWordStart;

                typedBuffer.append(wrong);
                mistakeActive = true;
                mistakeDeleteRemaining = deleteCount;
                mistakePauseUntilNanos = now + 150_000_000L; // 150ms hesitation before fixing

                // Apply a small, natural slowdown (0.8s–1.4s) to mimic hesitation
                double slowdown = 0.8 + rng.nextDouble() * 0.6;
                totalTypingSeconds = Math.max(0.0, totalTypingSeconds - slowdown);

                charAccumulator -= 1.0;
                continue;
            }

            // Normal character (no mistake)
            if (correct == '\n') {
                // Add newline plus 2 empty lines
                typedBuffer.append("\n\n\n");
            } else {
                typedBuffer.append(correct);
            }
            rawIndex++;
            charAccumulator -= 1.0;
        }
    }
    
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g;
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        
        // White background
        g2d.setColor(Color.WHITE);
        g2d.fillRect(0, 0, getWidth(), getHeight());
        
        int titleEnd = TITLE_FADE_IN + TITLE_HOLD;
        int bylineEnd = titleEnd + BYLINE_FADE_IN + BYLINE_HOLD;
        int fadeOutEnd = bylineEnd + FADE_OUT;
        
        // Draw title "Writer's Block" (intro fade in/out)
        float titleOpacity = 0;
        if (frame < TITLE_FADE_IN) {
            titleOpacity = frame / (float)TITLE_FADE_IN;
        } else if (frame < bylineEnd) {
            titleOpacity = 1.0f;
        } else if (frame < fadeOutEnd) {
            titleOpacity = 1.0f - (frame - bylineEnd) / (float)FADE_OUT;
        }
        
        if (titleOpacity > 0) {
            int alpha = (int)(titleOpacity * 255);
            g2d.setColor(new Color(0, 0, 0, alpha));
            g2d.setFont(new Font("Georgia", Font.BOLD, 72));
            FontMetrics fm = g2d.getFontMetrics();
            String title = "Writer's Block";
            int x = (getWidth() - fm.stringWidth(title)) / 2;
            int y = getHeight() / 2 - 40;
            g2d.drawString(title, x, y);
        }
        
        // Draw byline "by ari khan"
        float bylineOpacity = 0;
        if (frame > titleEnd && frame < titleEnd + BYLINE_FADE_IN) {
            bylineOpacity = (frame - titleEnd) / (float)BYLINE_FADE_IN;
        } else if (frame >= titleEnd + BYLINE_FADE_IN && frame < bylineEnd) {
            bylineOpacity = 1.0f;
        } else if (frame >= bylineEnd && frame < fadeOutEnd) {
            bylineOpacity = 1.0f - (frame - bylineEnd) / (float)FADE_OUT;
        }
        
        if (bylineOpacity > 0) {
            int alpha = (int)(bylineOpacity * 255);
            g2d.setColor(new Color(0, 0, 0, alpha));
            g2d.setFont(new Font("Georgia", Font.ITALIC, 28));
            FontMetrics fm = g2d.getFontMetrics();
            String byline = "By Ari Khan";
            int x = (getWidth() - fm.stringWidth(byline)) / 2;
            int y = getHeight() / 2 + 40;
            g2d.drawString(byline, x, y);
        }
        
        // Draw frames (main frame at 75% height + two comment boxes below)
        if (frame > fadeOutEnd) {
            float frameOpacity = Math.min(1.0f, (frame - fadeOutEnd) / (float)FRAME_FADE_IN);
            int alpha = (int)(frameOpacity * 255);
            
            int margin = (int)(Math.min(getWidth(), getHeight()) * 0.05);
            
            // Main frame (75% of screen height from top)
            int mainFrameX = margin;
            int mainFrameY = margin;
            int mainFrameWidth = getWidth() - (margin * 2);
            int mainFrameHeight = (int)(getHeight() * 0.75) - (margin * 2);
            
            g2d.setColor(new Color(0, 0, 0, alpha));
            g2d.setStroke(new BasicStroke(8));
            g2d.drawRoundRect(mainFrameX, mainFrameY, mainFrameWidth, mainFrameHeight, 30, 30);
            
            // Draw document title with fade transitions (essay titles only)
            if (!documentTitle.isEmpty()) {
                long now = System.nanoTime();
                int titleAlpha = alpha;
                
                if (titleFadeOutStartNanos > 0) {
                    long elapsed = now - titleFadeOutStartNanos;
                    
                    // Fade out old title
                    if (elapsed < TITLE_FADE_OUT_DURATION) {
                        float fadeOutOpacity = 1.0f - (elapsed / (float)TITLE_FADE_OUT_DURATION);
                        titleAlpha = (int)(fadeOutOpacity * alpha);
                        g2d.setColor(new Color(0, 0, 0, titleAlpha));
                        g2d.setFont(new Font("Georgia", Font.BOLD, 18));
                        g2d.drawString(oldTitle, mainFrameX + 20, mainFrameY + 35);
                    } else {
                        // Fade-out complete, start fade-in
                        if (titleFadeInStartNanos < 0) {
                            titleFadeInStartNanos = now;
                        }
                        
                        long fadeInElapsed = now - titleFadeInStartNanos;
                        if (fadeInElapsed < TITLE_FADE_IN_DURATION) {
                            float fadeInOpacity = fadeInElapsed / (float)TITLE_FADE_IN_DURATION;
                            titleAlpha = (int)(fadeInOpacity * alpha);
                            g2d.setColor(new Color(0, 0, 0, titleAlpha));
                            g2d.setFont(new Font("Georgia", Font.BOLD, 18));
                            g2d.drawString(newTitle, mainFrameX + 20, mainFrameY + 35);
                        } else {
                            // Fade-in complete, set as current and clear transition state
                            documentTitle = newTitle;
                            titleFadeOutStartNanos = -1;
                            titleFadeInStartNanos = -1;
                            oldTitle = "";
                            newTitle = "";
                            // Draw normal title
                            g2d.setColor(new Color(0, 0, 0, titleAlpha));
                            g2d.setFont(new Font("Georgia", Font.BOLD, 18));
                            g2d.drawString(documentTitle, mainFrameX + 20, mainFrameY + 35);
                        }
                    }
                } else {
                    // Normal title display (no transition)
                    g2d.setColor(new Color(0, 0, 0, titleAlpha));
                    g2d.setFont(new Font("Georgia", Font.BOLD, 18));
                    g2d.drawString(documentTitle, mainFrameX + 20, mainFrameY + 35);
                }
            }
            
            // Draw 24-hour digital clock in top right corner
            int totalMinutes = (int)clockMinutes;
            int hours = (totalMinutes / 60) % 24;
            int minutes = totalMinutes % 60;
            String clockTime = String.format("%02d:%02d", hours, minutes);
            g2d.setColor(new Color(0, 0, 0, alpha));
            g2d.setFont(new Font("Courier New", Font.PLAIN, 16));
            FontMetrics clockFm = g2d.getFontMetrics();
            int clockWidth = clockFm.stringWidth(clockTime);
            int clockX = mainFrameX + mainFrameWidth - clockWidth - 20;
            int clockY = mainFrameY + 30;
            g2d.drawString(clockTime, clockX, clockY);
            
            // Left comment box (in the bottom 25% space) - 75% of total width
            int commentY = (int)(getHeight() * 0.75);
            int totalCommentWidth = getWidth() - (margin * 3);
            int leftCommentWidth = (int)(totalCommentWidth * 0.75);
            int rightCommentWidth = totalCommentWidth - leftCommentWidth;
            int commentHeight = getHeight() - commentY - margin;
            
            g2d.setColor(new Color(0, 0, 0, alpha));
            g2d.drawRoundRect(margin, commentY, leftCommentWidth, commentHeight, 15, 15);
            
            // Right comment box - 25% of total width
            int rightCommentX = margin + leftCommentWidth + margin;
            g2d.drawRoundRect(rightCommentX, commentY, rightCommentWidth, commentHeight, 15, 15);

            // Draw typed body text with visual cursor inside the main frame
            if (typedBuffer.length() > 0 || (rawText != null && rawIndex < rawText.length())) {
                g2d.setColor(new Color(0, 0, 0, alpha));
                g2d.setFont(new Font("Georgia", Font.PLAIN, 18));
                FontMetrics tfm = g2d.getFontMetrics();
                int textX = mainFrameX + 20;
                int textY = mainFrameY + 60; // start below title area
                int textWidth = mainFrameWidth - 40;
                int lineHeight = tfm.getHeight();

                String visible = typedBuffer.toString();
                java.util.List<String> lines = wrapTextToLines(g2d, visible, textWidth);

                int contentHeight = mainFrameHeight - (textY - mainFrameY) - 20; // bottom padding
                int maxLines = Math.max(1, contentHeight / lineHeight);
                int startLine = Math.max(0, lines.size() - maxLines); // scroll older lines off the top

                int drawY = textY;
                for (int i = startLine; i < lines.size(); i++) {
                    g2d.drawString(lines.get(i), textX, drawY);
                    drawY += lineHeight;
                }

                // Blinking caret (visible ~500ms on/off), lighter color
                boolean hasMoreToType = rawText != null && rawIndex < rawText.length();
                if (hasMoreToType) {
                    boolean showCaret = ((int)(totalTypingSeconds * 2)) % 2 == 0; // toggle every ~0.5s
                    if (showCaret) {
                        if (!lines.isEmpty()) {
                            int caretHeight = tfm.getAscent();
                            int caretWidth = 2;
                            int lastIndex = Math.max(startLine, lines.size() - 1);
                            int cx = textX + tfm.stringWidth(lines.get(lastIndex));
                            int baselineY = drawY - lineHeight; // baseline of last drawn line
                            int cy = baselineY - tfm.getAscent();
                            int caretAlpha = Math.max(30, alpha / 2); // lighter caret
                            Color prev = g2d.getColor();
                            g2d.setColor(new Color(0, 0, 0, caretAlpha));
                            g2d.fillRect(cx, cy, caretWidth, caretHeight);
                            g2d.setColor(prev);
                        }
                    }
                }
            }

            // Render comments in the bottom boxes with fade-in
            g2d.setFont(new Font("Georgia", Font.PLAIN, 14));
            FontMetrics cfm = g2d.getFontMetrics();
            int padding = 12;
            int leftInnerX = margin + padding;
            int leftInnerW = leftCommentWidth - padding * 2;
            int leftInnerH = commentHeight - padding * 2;
            int leftInnerY = commentY + padding + cfm.getAscent();
            int rightInnerX = rightCommentX + padding;
            int rightInnerW = rightCommentWidth - padding * 2;
            int rightInnerH = commentHeight - padding * 2;
            int rightInnerY = commentY + padding + cfm.getAscent();

            long now = System.nanoTime();

            if (currentLeftComment != null && !currentLeftComment.isEmpty()) {
                // Calculate fade-in opacity
                float commentOpacity = 1.0f;
                if (leftCommentStartNanos > 0) {
                    long elapsed = now - leftCommentStartNanos;
                    if (elapsed < COMMENT_FADE_DURATION_NANOS) {
                        commentOpacity = elapsed / (float)COMMENT_FADE_DURATION_NANOS;
                    }
                }
                int commentAlpha = (int)(commentOpacity * alpha);
                g2d.setColor(new Color(0, 0, 0, commentAlpha));
                
                java.util.List<String> llines = wrapTextToLines(g2d, currentLeftComment, leftInnerW);
                int maxL = Math.max(1, leftInnerH / cfm.getHeight());
                int dy = leftInnerY;
                for (int i = 0; i < Math.min(maxL, llines.size()); i++) {
                    g2d.drawString(llines.get(i), leftInnerX, dy);
                    dy += cfm.getHeight();
                }
            }
            if (currentRightComment != null && !currentRightComment.isEmpty()) {
                // Calculate fade-in opacity
                float commentOpacity = 1.0f;
                if (rightCommentStartNanos > 0) {
                    long elapsed = now - rightCommentStartNanos;
                    if (elapsed < COMMENT_FADE_DURATION_NANOS) {
                        commentOpacity = elapsed / (float)COMMENT_FADE_DURATION_NANOS;
                    }
                }
                int commentAlpha = (int)(commentOpacity * alpha);
                g2d.setColor(new Color(0, 0, 0, commentAlpha));
                
                java.util.List<String> rlines = wrapTextToLines(g2d, currentRightComment, rightInnerW);
                int maxR = Math.max(1, rightInnerH / cfm.getHeight());
                int dy = rightInnerY;
                for (int i = 0; i < Math.min(maxR, rlines.size()); i++) {
                    g2d.drawString(rlines.get(i), rightInnerX, dy);
                    dy += cfm.getHeight();
                }
            }
        }
    }

    // Wrap text into lines without drawing; word-safe wrapping with newline support
    private java.util.List<String> wrapTextToLines(Graphics2D g2d, String text, int maxWidth) {
        FontMetrics fm = g2d.getFontMetrics();
        java.util.List<String> result = new java.util.ArrayList<>();
        if (text == null || text.isEmpty()) return result;

        StringBuilder line = new StringBuilder();
        int i = 0;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (c == '\n') {
                result.add(line.toString());
                line.setLength(0);
                i++;
                continue;
            }

            // collapse spaces to single space for wrapping purposes
            if (Character.isWhitespace(c)) {
                if (line.length() > 0 && line.charAt(line.length() - 1) != ' ') {
                    line.append(' ');
                }
                while (i < text.length() && Character.isWhitespace(text.charAt(i)) && text.charAt(i) != '\n') {
                    i++;
                }
                continue;
            }

            int start = i;
            while (i < text.length()) {
                char ch = text.charAt(i);
                if (ch == '\n' || Character.isWhitespace(ch)) break;
                i++;
            }
            String word = text.substring(start, i);

            if (line.length() == 0) {
                line.append(word);
            } else {
                String candidate = line.toString() + word;
                if (fm.stringWidth(candidate) <= maxWidth) {
                    line.setLength(0);
                    line.append(candidate);
                } else {
                    result.add(line.toString());
                    line.setLength(0);
                    line.append(word);
                }
            }
        }
        result.add(line.toString());
        return result;
    }

    // Marker handling
    private boolean handleMarker(String tag, long nowNanos) {
        try {
            if (tag.startsWith("TITLE_")) {
                int n = Integer.parseInt(tag.substring(6));
                if (n > 0 && n - 1 < titleLines.size()) {
                    // Start title fade transition
                    oldTitle = documentTitle;
                    newTitle = titleLines.get(n - 1);
                    titleFadeOutStartNanos = nowNanos;
                    titleFadeInStartNanos = -1;
                    // Clear comments immediately
                    currentLeftComment = "";
                    currentRightComment = "";
                    leftCommentClearUntilNanos = -1;
                    rightCommentClearUntilNanos = -1;
                    leftCommentStartNanos = -1;
                    rightCommentStartNanos = -1;
                }
                return true;
            }
            if (tag.startsWith("LEFT_COMMENT_")) {
                int n = Integer.parseInt(tag.substring(13));
                if (n > 0 && n - 1 < leftComments.size()) currentLeftComment = leftComments.get(n - 1);
                leftCommentStartNanos = nowNanos;
                // Duration based on comment length: 2s + 1s per 50 chars (min 3s, max 10s)
                long duration = Math.min(10_000_000_000L, Math.max(3_000_000_000L, 2_000_000_000L + (currentLeftComment.length() / 50) * 1_000_000_000L));
                leftCommentClearUntilNanos = nowNanos + duration;
                pauseUntilNanos = nowNanos + duration;
                totalTypingSeconds = 0.0; // reset speed accumulator
                return true;
            }
            if (tag.startsWith("RIGHT_COMMENT_")) {
                int n = Integer.parseInt(tag.substring(14));
                if (n > 0 && n - 1 < rightComments.size()) currentRightComment = rightComments.get(n - 1);
                rightCommentStartNanos = nowNanos;
                // Duration based on comment length: 2s + 1s per 50 chars (min 3s, max 10s)
                long duration = Math.min(10_000_000_000L, Math.max(3_000_000_000L, 2_000_000_000L + (currentRightComment.length() / 50) * 1_000_000_000L));
                rightCommentClearUntilNanos = nowNanos + duration;
                pauseUntilNanos = nowNanos + duration;
                totalTypingSeconds = 0.0; // reset speed accumulator
                return true;
            }
            if (tag.equals("PAUSE")) {
                pauseUntilNanos = nowNanos + 1_000_000_000L; // 1s
                totalTypingSeconds = 0.0; // reset speed to restart from MIN_WPM
                return true;
            }
        } catch (Exception ignore) { }
        return false; // not a known marker
    }
}

