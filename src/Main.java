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
    private final int TITLE_FADE_IN = 30;
    private final int TITLE_HOLD = 60;
    private final int BYLINE_FADE_IN = 30;
    private final int BYLINE_HOLD = 30;
    private final int FADE_OUT = 30;
    private final int FRAME_FADE_IN = 30;
    
    private String documentTitle = "";
    private List<String> titleLines = new ArrayList<>();
    
    // Typing effect state
    private String fullText = "";           // processed text to display (markers removed)
    private int typedChars = 0;              // number of characters currently typed
    private double charAccumulator = 0.0;    // fractional character accumulator
    private long lastTimeNanos = -1;         // last timestamp for typing update
    private double totalTypingSeconds = 0.0; // total typing time since start
    private final double MIN_WPM = 50.0;     // starting speed
    private final double MAX_WPM = 800.0;    // max speed (increased)
    private final double ACCEL_DURATION = 8.0; // faster acceleration to max speed
    
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

            // Prepare the body text: remove bracketed control markers like [TITLE_1], [LEFT_COMMENT_1], [PAUSE], etc.
            // Keep the actual prose only.
            fullText = raw.replaceAll("\\[[A-Z_0-9]+\\]", "").trim();
        } catch (Exception e) {
            documentTitle = "";
            fullText = "";
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
        if (fullText == null || fullText.isEmpty() || typedChars >= fullText.length()) {
            return;
        }

        long now = System.nanoTime();
        if (lastTimeNanos < 0) {
            lastTimeNanos = now;
            return;
        }

        double dt = (now - lastTimeNanos) / 1_000_000_000.0; // seconds
        lastTimeNanos = now;
        totalTypingSeconds += dt;

        // Linear acceleration from MIN_WPM to MAX_WPM over ACCEL_DURATION seconds
        double t = Math.min(1.0, totalTypingSeconds / ACCEL_DURATION);
        // Ease-in (quadratic) to increase acceleration noticeably
        double tEased = t * t;
        double currentWpm = MIN_WPM + (MAX_WPM - MIN_WPM) * tEased;
        double charsPerSecond = (currentWpm * 5.0) / 60.0; // 5 chars per "word" convention
        double charsThisTick = charsPerSecond * dt;

        charAccumulator += charsThisTick;
        int advance = (int)Math.floor(charAccumulator);
        if (advance > 0) {
            typedChars = Math.min(fullText.length(), typedChars + advance);
            charAccumulator -= advance;
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
        
        // Draw title "Writer's Block"
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
            
            // Draw document title at top of main frame
            if (!documentTitle.isEmpty()) {
                g2d.setColor(new Color(0, 0, 0, alpha));
                g2d.setFont(new Font("Georgia", Font.BOLD, 18));
                g2d.drawString(documentTitle, mainFrameX + 20, mainFrameY + 35);
            }
            
            // Left comment box (in the bottom 25% space)
            int commentY = (int)(getHeight() * 0.75);
            int commentWidth = (getWidth() - (margin * 3)) / 2;
            int commentHeight = getHeight() - commentY - margin;
            
            g2d.setColor(new Color(0, 0, 0, alpha));
            g2d.drawRoundRect(margin, commentY, commentWidth, commentHeight, 15, 15);
            
            // Right comment box
            int rightCommentX = margin + commentWidth + margin;
            g2d.drawRoundRect(rightCommentX, commentY, commentWidth, commentHeight, 15, 15);

            // Draw typed body text with visual cursor inside the main frame
            if (!fullText.isEmpty()) {
                g2d.setColor(new Color(0, 0, 0, alpha));
                g2d.setFont(new Font("Georgia", Font.PLAIN, 18));
                FontMetrics tfm = g2d.getFontMetrics();
                int textX = mainFrameX + 20;
                int textY = mainFrameY + 60; // start below title area
                int textWidth = mainFrameWidth - 40;
                int lineHeight = tfm.getHeight();

                String visible = fullText.substring(0, Math.min(typedChars, fullText.length()));
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
                if (typedChars < fullText.length()) {
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

    // Draws wrapped text within the given width. Returns the caret position (x,y baseline) at the end.
    private Point drawWrappedText(Graphics2D g2d, String text, int x, int y, int maxWidth, int lineHeight) {
        FontMetrics fm = g2d.getFontMetrics();
        int cursorX = x;
        int cursorY = y + fm.getAscent();
        if (text == null || text.isEmpty()) {
            return new Point(cursorX, cursorY);
        }

        StringBuilder line = new StringBuilder();
        int i = 0;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (c == '\n') {
                // Draw current line and move to next
                g2d.drawString(line.toString(), x, y);
                y += lineHeight;
                line.setLength(0);
                i++;
                continue;
            }

            // Consume any whitespace as a single space (avoid leading spaces)
            if (Character.isWhitespace(c)) {
                // Only add a space if line currently has content
                if (line.length() > 0) {
                    // Space alone won't overflow; just add it
                    line.append(' ');
                }
                // advance through contiguous whitespace
                while (i < text.length() && Character.isWhitespace(text.charAt(i)) && text.charAt(i) != '\n') {
                    i++;
                }
                continue;
            }

            // Consume a word (sequence of non-whitespace, non-newline)
            int start = i;
            while (i < text.length()) {
                char ch = text.charAt(i);
                if (ch == '\n' || Character.isWhitespace(ch)) break;
                i++;
            }
            String word = text.substring(start, i);

            // Decide to put word on current line or wrap
            String candidate = (line.length() == 0) ? word : line.toString() + word;
            int width = fm.stringWidth(candidate);
            if (width <= maxWidth || line.length() == 0) {
                // Fits on this line (or line empty even if long word)
                line.setLength(0);
                line.append(candidate);
            } else {
                // Draw current line and move to next, start with this word
                g2d.drawString(line.toString(), x, y);
                y += lineHeight;
                line.setLength(0);
                line.append(word);
            }
        }

        // Draw remainder
        g2d.drawString(line.toString(), x, y);
        cursorX = x + fm.stringWidth(line.toString());
        cursorY = y + fm.getAscent();
        return new Point(cursorX, cursorY);
    }
}
