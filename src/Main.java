import javax.swing.*;
import java.awt.*;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import javax.sound.sampled.*;
import javax.swing.Timer;
import java.io.File;

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
    private final int PROMPT_FADE_IN = 30;
    private final int FRAME_FADE_IN = 15;
    
    private String documentTitle = "";
    private String oldTitle = "";  
    private String newTitle = "";  
    private long titleFadeOutStartNanos = -1;  
    private long titleFadeInStartNanos = -1;   
    private final long TITLE_FADE_OUT_DURATION = 300_000_000L;  
    private final long TITLE_FADE_IN_DURATION = 300_000_000L;   
    private List<String> titleLines = new ArrayList<>();
    private List<String> leftComments = new ArrayList<>();
    private List<String> rightComments = new ArrayList<>();
    private String currentLeftComment = "";
    private String currentRightComment = "";
    
    
    private String rawText = "";            
    private StringBuilder typedBuffer = new StringBuilder(); 
    private int rawIndex = 0;                
    private double charAccumulator = 0.0;    
    private long lastTimeNanos = -1;         
    private double totalTypingSeconds = 0.0; 
    private final double MIN_WPM = 100.0;     
    private final double MAX_WPM = 1200.0;    
    private final double ACCEL_DURATION = 5.0; 
    private long pauseUntilNanos = -1;       
    private long leftCommentClearUntilNanos = -1;  
    private long rightCommentClearUntilNanos = -1; 
    private long leftCommentStartNanos = -1;  
    private long rightCommentStartNanos = -1; 
    private final long COMMENT_FADE_DURATION_NANOS = 300_000_000L; 
    private final Random rng = new Random(); 
    private boolean mistakeActive = false;   
    private int mistakeDeleteRemaining = 0;  
    private long mistakePauseUntilNanos = -1;    
    private long mistakeCooldownUntilNanos = -1; 
    private double clockMinutes = 0.0;  
    private long labelFadeStartNanos = -1;  
    private final long LABEL_FADE_IN = 500_000_000L; 
    private final long LABEL_FADE_HOLD = 3_000_000_000L; 
    private final long LABEL_FADE_OUT = 500_000_000L; 
    private long lastTypeNanos = -1; 
    private final Color BG_TOP = new Color(246, 244, 239);
    private final Color BG_BOTTOM = new Color(232, 229, 223);
    private final Color FRAME_FILL = new Color(255, 255, 255, 235);
    private final Color FRAME_STROKE = new Color(34, 41, 57, 220);
    private final Color COMMENT_LEFT_FILL = new Color(255, 243, 226, 210);
    private final Color COMMENT_RIGHT_FILL = new Color(226, 240, 255, 210);
    private final Color COMMENT_BORDER = new Color(34, 41, 57, 190);
    private Clip typingLoop;
    private boolean typingLoopReady = false;
    private String typingLoopPath = "";
    private FloatControl typingRateControl;
    private FloatControl typingGainControl;
    private float typingBaseSampleRate = -1f;
    private boolean waitingToStart = true;
    private int startPressedFrame = -1;
    private int endSequenceStartFrame = -1;
    private final int END_FADE_OUT = 30;
    private final int END_FADE_IN = 30;
    
    public AnimationPanel() {
        loadFiles();
        initTypingLoop();
        
        setFocusable(true);
        addKeyListener(new java.awt.event.KeyAdapter() {
            @Override
            public void keyPressed(java.awt.event.KeyEvent e) {
                if (waitingToStart && e.getKeyCode() == java.awt.event.KeyEvent.VK_SPACE) {
                    waitingToStart = false;
                    startPressedFrame = frame;
                    lastTypeNanos = System.nanoTime();
                    repaint();
                }
            }
        });
        
        Timer timer = new Timer(30, e -> {
            frame++;
            updateTyping();
            repaint();
        });
        timer.start();
        
        SwingUtilities.invokeLater(() -> requestFocusInWindow());
    }
    
    private void loadFiles() {
        try {
            String writingPath = "C:\\Users\\ariba\\Desktop\\Program Files\\ICS\\School\\visual-essay\\src\\writing.txt";
            String raw = new String(Files.readAllBytes(Paths.get(writingPath)));
            
            
            String titlesPath = "C:\\Users\\ariba\\Desktop\\Program Files\\ICS\\School\\visual-essay\\src\\titles.txt";
            titleLines = Files.readAllLines(Paths.get(titlesPath));
            
            
            String leftPath = "C:\\Users\\ariba\\Desktop\\Program Files\\ICS\\School\\visual-essay\\src\\left_comments.txt";
            String rightPath = "C:\\Users\\ariba\\Desktop\\Program Files\\ICS\\School\\visual-essay\\src\\right_comments.txt";
            if (Files.exists(Paths.get(leftPath))) leftComments = Files.readAllLines(Paths.get(leftPath));
            if (Files.exists(Paths.get(rightPath))) rightComments = Files.readAllLines(Paths.get(rightPath));
            
            
            int start = raw.indexOf("[TITLE_");
            if (start >= 0) {
                int end = raw.indexOf("]", start);
                if (end > start) {
                    
                    String marker = raw.substring(start + 7, end);
                    try {
                        int lineNum = Integer.parseInt(marker) - 1; 
                        if (lineNum >= 0 && lineNum < titleLines.size()) {
                            documentTitle = titleLines.get(lineNum);
                        }
                    } catch (NumberFormatException ex) {
                        documentTitle = "";
                    }
                }
            }

            
            rawText = raw;
            typedBuffer.setLength(0);
            rawIndex = 0;
        } catch (Exception e) {
            documentTitle = "";
            e.printStackTrace();
        }
    }

    private void initTypingLoop() {
        String[] candidates = {
            "keyboard.wav",
            "src" + File.separator + "keyboard.wav",
            "bin" + File.separator + "keyboard.wav"
        };
        for (String path : candidates) {
            try {
                File f = new File(path);
                if (!f.exists()) continue;
                AudioInputStream ais = AudioSystem.getAudioInputStream(f);
                AudioFormat base = ais.getFormat();
                AudioFormat decoded = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, base.getSampleRate(), 16, base.getChannels(), base.getChannels() * 2, base.getSampleRate(), false);
                AudioInputStream dais = AudioSystem.getAudioInputStream(decoded, ais);
                Clip c = AudioSystem.getClip();
                c.open(dais);
                typingLoop = c;
                typingLoopReady = true;
                typingLoopPath = f.getAbsolutePath();
                if (c.isControlSupported(FloatControl.Type.SAMPLE_RATE)) {
                    typingRateControl = (FloatControl)c.getControl(FloatControl.Type.SAMPLE_RATE);
                    typingBaseSampleRate = typingRateControl.getValue();
                }
                if (c.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
                    typingGainControl = (FloatControl)c.getControl(FloatControl.Type.MASTER_GAIN);
                }
                System.out.println("Loaded typing loop: " + typingLoopPath);
                break;
            } catch (Exception ex) {
                typingLoop = null;
                typingLoopReady = false;
                typingLoopPath = "";
                typingRateControl = null;
                typingGainControl = null;
                typingBaseSampleRate = -1f;
            }
        }
        if (!typingLoopReady) {
            System.out.println("keyboard.mp3 not loaded (codec or path issue). Place keyboard.mp3 in project root or src/.");
        }
    }

    private int getFadeOutEndFrame() {
        int titleEnd = TITLE_FADE_IN + TITLE_HOLD;
        int bylineEnd = titleEnd + BYLINE_FADE_IN + BYLINE_HOLD;
        return bylineEnd + FADE_OUT;
    }

    private void startTypingLoop() {
        if (!typingLoopReady || typingLoop == null) return;
        if (!typingLoop.isRunning()) {
            typingLoop.setFramePosition(0);
            typingLoop.loop(Clip.LOOP_CONTINUOUSLY);
        }
    }

    private void stopTypingLoop() {
        if (!typingLoopReady || typingLoop == null) return;
        typingLoop.stop();
    }

    private void updateTypingLoopSpeed(double currentWpm) {
        if (!typingLoopReady || typingLoop == null) return;
        double norm = Math.max(0.0, Math.min(1.0, (currentWpm - MIN_WPM) / (MAX_WPM - MIN_WPM)));
        double rateFactor = 0.75 + norm * 2.0; 
        if (typingRateControl != null && typingBaseSampleRate > 0) {
            float desired = (float)Math.max(typingRateControl.getMinimum(), Math.min(typingRateControl.getMaximum(), typingBaseSampleRate * rateFactor));
            typingRateControl.setValue(desired);
        } else if (typingGainControl != null) {
            float gainDb = (float)((rateFactor - 1.0) * 1.5); 
            float clamped = Math.max(typingGainControl.getMinimum(), Math.min(typingGainControl.getMaximum(), gainDb));
            typingGainControl.setValue(clamped);
        }
    }

    private void updateTyping() {
        
        if (frame <= getFadeOutEndFrame() || waitingToStart) {
            lastTimeNanos = -1; 
            return;
        }
        long now = System.nanoTime();
        if (lastTimeNanos < 0) {
            lastTimeNanos = now;
            return;
        }

        if (pauseUntilNanos > 0 && now < pauseUntilNanos) {
            stopTypingLoop();
            return;
        }
        if (pauseUntilNanos > 0 && now >= pauseUntilNanos) {
            pauseUntilNanos = -1;
            lastTimeNanos = now; 
        }

        if (typingLoopReady && lastTypeNanos > 0 && (now - lastTypeNanos) > 2_000_000_000L) {
            stopTypingLoop();
        }

        
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

        
        if (mistakeActive) {
            if (mistakePauseUntilNanos > 0 && now < mistakePauseUntilNanos) {
                return;
            }
            if (mistakeDeleteRemaining > 0 && typedBuffer.length() > 0) {
                typedBuffer.deleteCharAt(typedBuffer.length() - 1);
                mistakeDeleteRemaining--;
                lastTypeNanos = now;
                startTypingLoop();
                return;
            } else {
                mistakeActive = false;
                mistakeDeleteRemaining = 0;
                mistakePauseUntilNanos = -1;
                mistakeCooldownUntilNanos = now + 500_000_000L; 
            }
        }

        double dt = (now - lastTimeNanos) / 1_000_000_000.0; 
        lastTimeNanos = now;
        totalTypingSeconds += dt;

        
        double t = Math.min(1.0, totalTypingSeconds / ACCEL_DURATION);
        double tEased = t * t;
        double currentWpm = MIN_WPM + (MAX_WPM - MIN_WPM) * tEased;
        double charsPerSecond = (currentWpm * 5.0) / 60.0;
        double charsThisTick = charsPerSecond * dt;
        updateTypingLoopSpeed(currentWpm);

        
        double clockSpeed = 1.0; 
        if (pauseUntilNanos > 0 || leftCommentClearUntilNanos > 0 || rightCommentClearUntilNanos > 0) {
            clockSpeed = 0.3; 
        } else {
            clockSpeed = currentWpm / MIN_WPM; 
        }
        clockMinutes += (dt / 60.0) * clockSpeed * 16.0; 

        charAccumulator += charsThisTick;
        while (charAccumulator >= 1.0) {
            if (rawIndex >= rawText.length()) break; 

            
            if (rawText.charAt(rawIndex) == '[') {
                int close = rawText.indexOf(']', rawIndex);
                if (close > rawIndex) {
                    String tag = rawText.substring(rawIndex + 1, close); 
                    if (handleMarker(tag, now)) {
                        rawIndex = close + 1; 
                        
                        if (pauseUntilNanos > 0 && System.nanoTime() < pauseUntilNanos) {
                            break;
                        }
                        continue; 
                    }
                }
            }

            
            char correct = rawText.charAt(rawIndex);

                        boolean triggerMistake = !mistakeActive
                            && (mistakeCooldownUntilNanos < 0 || now >= mistakeCooldownUntilNanos)
                            && !Character.isWhitespace(correct)
                            && rng.nextDouble() < 0.015; 

            if (triggerMistake) {
                
                char wrong = correct;
                if (Character.isLetter(correct)) {
                    char base = Character.isUpperCase(correct) ? 'A' : 'a';
                    do {
                        wrong = (char) (base + rng.nextInt(26));
                    } while (Character.toLowerCase(wrong) == Character.toLowerCase(correct));
                } else {
                    wrong = (char) ('a' + rng.nextInt(26));
                }

                
                int wordStart = typedBuffer.length();
                for (int i = typedBuffer.length() - 1; i >= 0; i--) {
                    char c = typedBuffer.charAt(i);
                    if (Character.isWhitespace(c)) {
                        wordStart = i + 1;
                        break;
                    }
                }
                int deleteCount = (typedBuffer.length() - wordStart) + 1; 

                
                int rawWordStart = rawIndex;
                while (rawWordStart > 0 && !Character.isWhitespace(rawText.charAt(rawWordStart - 1))) {
                    rawWordStart--;
                }
                rawIndex = rawWordStart;

                typedBuffer.append(wrong);
                mistakeActive = true;
                mistakeDeleteRemaining = deleteCount;
                mistakePauseUntilNanos = now + 150_000_000L; 
                lastTypeNanos = now;
                startTypingLoop();

                
                double slowdown = 0.8 + rng.nextDouble() * 0.6;
                totalTypingSeconds = Math.max(0.0, totalTypingSeconds - slowdown);

                charAccumulator -= 1.0;
                continue;
            }

            
            if (correct == '\n') {
                
                typedBuffer.append("\n\n\n");
            } else {
                typedBuffer.append(correct);
            }
            rawIndex++;
            charAccumulator -= 1.0;
            lastTypeNanos = now;
            startTypingLoop();
        }
    }
    
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g;
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        
        
        Paint oldPaint = g2d.getPaint();
        GradientPaint bg = new GradientPaint(0, 0, BG_TOP, getWidth(), getHeight(), BG_BOTTOM);
        g2d.setPaint(bg);
        g2d.fillRect(0, 0, getWidth(), getHeight());
        g2d.setPaint(oldPaint);
        
        int titleEnd = TITLE_FADE_IN + TITLE_HOLD;
        int bylineEnd = titleEnd + BYLINE_FADE_IN + BYLINE_HOLD;        
        
        float titleOpacity = 0;
        if (waitingToStart) {
            if (frame < TITLE_FADE_IN) {
                titleOpacity = frame / (float)TITLE_FADE_IN;
            } else {
                titleOpacity = 1.0f;
            }
        } else if (startPressedFrame >= 0) {
            int elapsedSincePress = frame - startPressedFrame;
            if (elapsedSincePress < FADE_OUT) {
                titleOpacity = 1.0f - (elapsedSincePress / (float)FADE_OUT);
            }
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
        
        
        float bylineOpacity = 0;
        if (waitingToStart) {
            if (frame > titleEnd && frame < titleEnd + BYLINE_FADE_IN) {
                bylineOpacity = (frame - titleEnd) / (float)BYLINE_FADE_IN;
            } else if (frame >= titleEnd + BYLINE_FADE_IN) {
                bylineOpacity = 1.0f;
            }
        } else if (startPressedFrame >= 0) {
            int elapsedSincePress = frame - startPressedFrame;
            if (elapsedSincePress < FADE_OUT) {
                bylineOpacity = 1.0f - (elapsedSincePress / (float)FADE_OUT);
            }
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
            
            float promptOpacity = 0;
            if (waitingToStart && frame >= bylineEnd) {
                int promptStart = bylineEnd;
                if (frame > promptStart && frame <= promptStart + PROMPT_FADE_IN) {
                    promptOpacity = (frame - promptStart) / (float)PROMPT_FADE_IN;
                } else if (frame > promptStart + PROMPT_FADE_IN) {
                    promptOpacity = 1.0f;
                }
            } else if (startPressedFrame >= 0) {
                int elapsedSincePress = frame - startPressedFrame;
                if (elapsedSincePress < FADE_OUT) {
                    promptOpacity = 1.0f - (elapsedSincePress / (float)FADE_OUT);
                }
            }
            
            if (promptOpacity > 0) {
                g2d.setFont(new Font("Georgia", Font.PLAIN, 18));
                FontMetrics promptFm = g2d.getFontMetrics();
                String prompt = "Press SPACE to start";
                int promptX = (getWidth() - promptFm.stringWidth(prompt)) / 2;
                int promptY = getHeight() / 2 + 90;
                int promptAlpha = (int)(promptOpacity * alpha);
                g2d.setColor(new Color(0, 0, 0, promptAlpha));
                g2d.drawString(prompt, promptX, promptY);
            }
        }
        
        
        if (!waitingToStart && startPressedFrame >= 0) {
            int contentStartFrame = startPressedFrame + FADE_OUT;
            if (frame > contentStartFrame) {
                float frameOpacity = Math.min(1.0f, (frame - contentStartFrame) / (float)FRAME_FADE_IN);
                int alpha = (int)(frameOpacity * 255);
                long nowFrame = System.nanoTime();
                if (labelFadeStartNanos < 0) labelFadeStartNanos = nowFrame;
            
            int margin = (int)(Math.min(getWidth(), getHeight()) * 0.05);
            
            
            int mainFrameX = margin;
            int mainFrameY = margin;
            int mainFrameWidth = getWidth() - (margin * 2);
            int mainFrameHeight = (int)(getHeight() * 0.70) - (margin * 2);
            
            double alphaRatio = alpha / 255.0;
            int fillA = (int)(FRAME_FILL.getAlpha() * alphaRatio);
            int strokeA = (int)(FRAME_STROKE.getAlpha() * alphaRatio);
            g2d.setColor(new Color(FRAME_FILL.getRed(), FRAME_FILL.getGreen(), FRAME_FILL.getBlue(), fillA));
            g2d.fillRoundRect(mainFrameX, mainFrameY, mainFrameWidth, mainFrameHeight, 30, 30);
            g2d.setColor(new Color(FRAME_STROKE.getRed(), FRAME_STROKE.getGreen(), FRAME_STROKE.getBlue(), strokeA));
            g2d.setStroke(new BasicStroke(8));
            g2d.drawRoundRect(mainFrameX, mainFrameY, mainFrameWidth, mainFrameHeight, 30, 30);
            
            
            if (!documentTitle.isEmpty()) {
                long now = System.nanoTime();
                int titleAlpha = alpha;
                
                if (titleFadeOutStartNanos > 0) {
                    long elapsed = now - titleFadeOutStartNanos;
                    
                    
                    if (elapsed < TITLE_FADE_OUT_DURATION) {
                        float fadeOutOpacity = 1.0f - (elapsed / (float)TITLE_FADE_OUT_DURATION);
                        titleAlpha = (int)(fadeOutOpacity * alpha);
                        g2d.setColor(new Color(0, 0, 0, titleAlpha));
                        g2d.setFont(new Font("Georgia", Font.BOLD, 18));
                        g2d.drawString(oldTitle, mainFrameX + 20, mainFrameY + 35);
                    } else {
                        
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
                            
                            documentTitle = newTitle;
                            titleFadeOutStartNanos = -1;
                            titleFadeInStartNanos = -1;
                            oldTitle = "";
                            newTitle = "";
                            
                            g2d.setColor(new Color(0, 0, 0, titleAlpha));
                            g2d.setFont(new Font("Georgia", Font.BOLD, 18));
                            g2d.drawString(documentTitle, mainFrameX + 20, mainFrameY + 35);
                        }
                    }
                } else {
                    
                    g2d.setColor(new Color(0, 0, 0, titleAlpha));
                    g2d.setFont(new Font("Georgia", Font.BOLD, 18));
                    g2d.drawString(documentTitle, mainFrameX + 20, mainFrameY + 35);
                }
            }
            
            
            int totalMinutes = (int)clockMinutes;
            int hours = (totalMinutes / 60) % 24;
            int minutes = totalMinutes % 60;
            String clockTime = String.format("%02d:%02d", hours, minutes);
            int clockA = (int)(220 * (alpha / 255.0));
            g2d.setColor(new Color(FRAME_STROKE.getRed(), FRAME_STROKE.getGreen(), FRAME_STROKE.getBlue(), clockA));
            g2d.setFont(new Font("Courier New", Font.PLAIN, 16));
            FontMetrics clockFm = g2d.getFontMetrics();
            int clockWidth = clockFm.stringWidth(clockTime);
            int clockX = mainFrameX + mainFrameWidth - clockWidth - 20;
            int clockY = mainFrameY + 30;
            g2d.drawString(clockTime, clockX, clockY);
            
            
            int commentY = (int)(getHeight() * 0.70);
            int totalCommentWidth = getWidth() - (margin * 3);
            int leftCommentWidth = (int)(totalCommentWidth * 0.75);
            int rightCommentWidth = totalCommentWidth - leftCommentWidth;
            int commentHeight = getHeight() - commentY - margin;
            
            int leftFillA = (int)(COMMENT_LEFT_FILL.getAlpha() * (alpha / 255.0));
            int rightFillA = (int)(COMMENT_RIGHT_FILL.getAlpha() * (alpha / 255.0));
            int borderA = (int)(COMMENT_BORDER.getAlpha() * (alpha / 255.0));
            g2d.setColor(new Color(COMMENT_LEFT_FILL.getRed(), COMMENT_LEFT_FILL.getGreen(), COMMENT_LEFT_FILL.getBlue(), leftFillA));
            g2d.fillRoundRect(margin, commentY, leftCommentWidth, commentHeight, 15, 15);
            
            int rightCommentX = margin + leftCommentWidth + margin;
            g2d.setColor(new Color(COMMENT_RIGHT_FILL.getRed(), COMMENT_RIGHT_FILL.getGreen(), COMMENT_RIGHT_FILL.getBlue(), rightFillA));
            g2d.fillRoundRect(rightCommentX, commentY, rightCommentWidth, commentHeight, 15, 15);

            g2d.setStroke(new BasicStroke(3));
            g2d.setColor(new Color(COMMENT_BORDER.getRed(), COMMENT_BORDER.getGreen(), COMMENT_BORDER.getBlue(), borderA));
            g2d.drawRoundRect(margin, commentY, leftCommentWidth, commentHeight, 15, 15);
            g2d.drawRoundRect(rightCommentX, commentY, rightCommentWidth, commentHeight, 15, 15);

            long labelElapsed = nowFrame - labelFadeStartNanos;
            double labelFadeRatio = 0.0;
            if (labelElapsed < LABEL_FADE_IN) {
                labelFadeRatio = Math.min(1.0, labelElapsed / (double)LABEL_FADE_IN);
            } else if (labelElapsed < LABEL_FADE_IN + LABEL_FADE_HOLD) {
                labelFadeRatio = 1.0;
            } else if (labelElapsed < LABEL_FADE_IN + LABEL_FADE_HOLD + LABEL_FADE_OUT) {
                long fadeOutElapsed = labelElapsed - (LABEL_FADE_IN + LABEL_FADE_HOLD);
                labelFadeRatio = 1.0 - Math.min(1.0, fadeOutElapsed / (double)LABEL_FADE_OUT);
            }
            int labelAlpha = (int)(alpha * labelFadeRatio);
            if (labelAlpha > 0) {
                g2d.setFont(new Font("Georgia", Font.BOLD, 22));
                FontMetrics labelFm = g2d.getFontMetrics();
                g2d.setColor(new Color(0, 0, 0, labelAlpha));
                int leftTextX = margin + 18;
                int leftTextY = commentY + 18 + labelFm.getAscent();
                g2d.drawString("Ari", leftTextX, leftTextY);
                int rightTextX = rightCommentX + 18;
                int rightTextY = commentY + 18 + labelFm.getAscent();
                g2d.drawString("Mr. Wu", rightTextX, rightTextY);
            }

            
            if (typedBuffer.length() > 0 || (rawText != null && rawIndex < rawText.length())) {
                g2d.setColor(new Color(0, 0, 0, alpha));
                g2d.setFont(new Font("Georgia", Font.PLAIN, 18));
                FontMetrics tfm = g2d.getFontMetrics();
                int textX = mainFrameX + 20;
                int textY = mainFrameY + 60; 
                int textWidth = mainFrameWidth - 40;
                int lineHeight = tfm.getHeight();

                String visible = typedBuffer.toString();
                java.util.List<String> lines = wrapTextToLines(g2d, visible, textWidth);

                int contentHeight = mainFrameHeight - (textY - mainFrameY) - 20; 
                int maxLines = Math.max(1, contentHeight / lineHeight);
                int startLine = Math.max(0, lines.size() - maxLines); 

                int drawY = textY;
                for (int i = startLine; i < lines.size(); i++) {
                    g2d.drawString(lines.get(i), textX, drawY);
                    drawY += lineHeight;
                }

                
                boolean hasMoreToType = rawText != null && rawIndex < rawText.length();
                if (hasMoreToType) {
                    long nowCaret = System.nanoTime();
                    boolean isIdle = lastTypeNanos > 0 && (nowCaret - lastTypeNanos) > 400_000_000L; 
                    boolean showCaret = isIdle ? ((nowCaret / 500_000_000L) % 2 == 0) : true; 
                    if (showCaret && !lines.isEmpty()) {
                        int caretHeight = tfm.getAscent();
                        int caretWidth = 2;
                        int lastIndex = Math.max(startLine, lines.size() - 1);
                        int cx = textX + tfm.stringWidth(lines.get(lastIndex));
                        int baselineY = drawY - lineHeight; 
                        int cy = baselineY - tfm.getAscent();
                        int caretAlpha = alpha; 
                        Color prev = g2d.getColor();
                        g2d.setColor(new Color(0, 0, 0, caretAlpha));
                        g2d.fillRect(cx, cy, caretWidth, caretHeight);
                        g2d.setColor(prev);
                    }
                } else {
                    stopTypingLoop();
                    if (endSequenceStartFrame < 0 && pauseUntilNanos < 0) {
                        endSequenceStartFrame = frame;
                    }
                }
            }

            // Check if end sequence should be displayed
            if (endSequenceStartFrame >= 0) {
                int elapsedEnd = frame - endSequenceStartFrame;
                float endAlpha = 0;
                
                if (elapsedEnd < END_FADE_OUT) {
                    // Fade out main content
                    endAlpha = 1.0f - (elapsedEnd / (float)END_FADE_OUT);
                } else if (elapsedEnd >= END_FADE_OUT && elapsedEnd < END_FADE_OUT + END_FADE_IN) {
                    // Fade in end screen
                    endAlpha = (elapsedEnd - END_FADE_OUT) / (float)END_FADE_IN;
                } else {
                    // Fully show end screen
                    endAlpha = 1.0f;
                }
                
                if (elapsedEnd >= END_FADE_OUT) {
                    int endScreenAlpha = (int)(endAlpha * 255);
                    g2d.setColor(new Color(0, 0, 0, endScreenAlpha));
                    g2d.setFont(new Font("Georgia", Font.BOLD, 72));
                    FontMetrics endFm = g2d.getFontMetrics();
                    String theEnd = "The End";
                    int endX = (getWidth() - endFm.stringWidth(theEnd)) / 2;
                    int endY = getHeight() / 2 - 40;
                    g2d.drawString(theEnd, endX, endY);
                    
                    g2d.setFont(new Font("Georgia", Font.ITALIC, 28));
                    FontMetrics thanksFm = g2d.getFontMetrics();
                    String thanks = "Thank you Mr. Wu";
                    int thanksX = (getWidth() - thanksFm.stringWidth(thanks)) / 2;
                    int thanksY = getHeight() / 2 + 50;
                    g2d.drawString(thanks, thanksX, thanksY);
                }
            }
            
            // Only render main content if end sequence hasn't started or is fading out
            if (endSequenceStartFrame < 0 || frame < endSequenceStartFrame + END_FADE_OUT) {
            
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
        }
    }

    
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

    
    private boolean handleMarker(String tag, long nowNanos) {
        try {
            if (tag.startsWith("TITLE_")) {
                int n = Integer.parseInt(tag.substring(6));
                if (n > 0 && n - 1 < titleLines.size()) {
                    
                    oldTitle = documentTitle;
                    newTitle = titleLines.get(n - 1);
                    titleFadeOutStartNanos = nowNanos;
                    titleFadeInStartNanos = -1;
                    
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
                
                long duration = Math.min(10_000_000_000L, Math.max(3_000_000_000L, 2_000_000_000L + (currentLeftComment.length() / 50) * 1_000_000_000L));
                leftCommentClearUntilNanos = nowNanos + duration;
                pauseUntilNanos = nowNanos + duration;
                totalTypingSeconds = 0.0; 
                return true;
            }
            if (tag.startsWith("RIGHT_COMMENT_")) {
                int n = Integer.parseInt(tag.substring(14));
                if (n > 0 && n - 1 < rightComments.size()) currentRightComment = rightComments.get(n - 1);
                rightCommentStartNanos = nowNanos;
                
                long duration = Math.min(10_000_000_000L, Math.max(3_000_000_000L, 2_000_000_000L + (currentRightComment.length() / 50) * 1_000_000_000L));
                rightCommentClearUntilNanos = nowNanos + duration;
                pauseUntilNanos = nowNanos + duration;
                totalTypingSeconds = 0.0; 
                return true;
            }
            if (tag.equals("PAUSE")) {
                pauseUntilNanos = nowNanos + 1_000_000_000L; 
                totalTypingSeconds = 0.0; 
                return true;
            }
        } catch (Exception ignore) { }
        return false; 
    }
}

