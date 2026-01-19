import javax.swing.*;
import java.awt.*;
import java.nio.file.*;
import java.util.*;
import java.util.List;

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
    
    public AnimationPanel() {
        loadFiles();
        
        Timer timer = new Timer(30, e -> {
            frame++;
            repaint();
        });
        timer.start();
    }
    
    private void loadFiles() {
        try {
            String writingPath = "C:\\Users\\ariba\\Desktop\\Program Files\\ICS\\School\\English\\src\\writing.txt";
            String raw = new String(Files.readAllBytes(Paths.get(writingPath)));
            
            // Load titles.txt
            String titlesPath = "C:\\Users\\ariba\\Desktop\\Program Files\\ICS\\School\\English\\src\\titles.txt";
            titleLines = Files.readAllLines(Paths.get(titlesPath));
            
            // Parse [TITLE_NUMBER] pattern
            int start = raw.indexOf("[TITLE");
            if (start >= 0) {
                int end = raw.indexOf("]", start);
                if (end > start) {
                    String marker = raw.substring(start + 6, end); // Skip "[TITLE"
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
        } catch (Exception e) {
            documentTitle = "";
            e.printStackTrace();
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
                g2d.setFont(new Font("Georgia", Font.PLAIN, 18));
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
        }
    }
}
