package com.activegoals;

import java.awt.*;
import java.awt.event.ItemEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.HierarchyEvent;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.HashSet;
import javax.inject.Inject;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.basic.BasicScrollBarUI;
import net.runelite.api.Skill;
import net.runelite.client.game.SkillIconManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.FontManager;

/** Compact native RuneLite sidebar. */
public class ActiveGoalsPanel extends PluginPanel
{
    private static final Color BG = new Color(35, 32, 28);
    private static final Color CARD = new Color(48, 43, 36);
    private static final Color GOLD = new Color(255, 214, 122);
    private static final Color TEXT = new Color(255, 255, 255);
    private static final Color MUTED = new Color(232, 225, 213);
    private static final DateTimeFormatter CREATION_TIME_FORMAT =
        DateTimeFormatter.ofPattern("d MMM uuuu, h:mm a", Locale.ENGLISH);
    private final ActiveGoalsPlugin plugin;
    private final SkillIconManager icons;
    private final JPanel content = new ViewportWidthPanel();
    private final Timer refreshTimer;
    private final Timer progressTooltipDelay;
    private final JToolTip progressTooltip = new JToolTip();
    private String hoveredProgressGoalId;
    private JProgressBar hoveredProgressBar;
    private final Timer levelTooltipDelay;
    private final JToolTip levelTooltip = new JToolTip();
    private String hoveredLevelGoalId;
    private JLabel hoveredLevelLabel;
    private boolean running;
    // Keep a pressed button in place until the current click completes.
    private final Set<JButton> pressedButtons = new HashSet<>();
    private long refreshGeneration;
    private boolean refreshPending;
    private JPanel goalForm;
    private String revealGoalId;
    private String confirmingRemovalId;

    @Inject
    public ActiveGoalsPanel(ActiveGoalsPlugin plugin, SkillIconManager icons)
    {
        super(false);
        this.plugin = plugin;
        this.icons = icons;
        setLayout(new BorderLayout());
        setBackground(BG);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBackground(BG);
        content.setBorder(new EmptyBorder(10, 7, 12, 7));
        JScrollPane scroll = new JScrollPane(content);
        scroll.setBorder(null);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        scroll.getVerticalScrollBar().setUI(new ActiveGoalsScrollBarUI());
        scroll.getVerticalScrollBar().setPreferredSize(new Dimension(8, 0));
        scroll.getViewport().setBackground(BG);
        scroll.getViewport().addChangeListener(e -> { positionProgressTooltip(); positionLevelTooltip(); });
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        JPanel top = new JPanel();
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        top.setBackground(BG);
        top.setBorder(new EmptyBorder(8, 7, 4, 7));
        JLabel title = label("Active Goals", GOLD, Font.BOLD);
        title.setIcon(journalIcon());
        title.setIconTextGap(8);
        top.add(centered(title));
        add(top, BorderLayout.NORTH);
        add(scroll, BorderLayout.CENTER);
        refreshTimer = new Timer(1500, e -> requestRefresh());
        progressTooltipDelay = new Timer(ToolTipManager.sharedInstance().getInitialDelay(), e -> showProgressTooltip());
        progressTooltipDelay.setRepeats(false);
        levelTooltipDelay = new Timer(ToolTipManager.sharedInstance().getInitialDelay(), e -> showLevelTooltip());
        levelTooltipDelay.setRepeats(false);
        addComponentListener(new ComponentAdapter()
        {
            @Override public void componentResized(ComponentEvent e) { positionProgressTooltip(); positionLevelTooltip(); }
        });
        addHierarchyListener(e ->
        {
            if ((e.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0 && !isShowing())
            {
                hideProgressTooltip();
                hideLevelTooltip();
            }
        });
    }

    private void showRemoveGoalConfirmation(GoalSnapshot goal)
    {
        if (goalForm != null || confirmingRemovalId != null) return;
        confirmingRemovalId = goal.id;
        requestRefresh();
    }

    void start()
    {
        running = true;
        refreshGeneration++;
        refreshTimer.start();
        requestRefresh();
    }

    void stop()
    {
        running = false;
        refreshGeneration++;
        refreshPending = false;
        pressedButtons.clear();
        goalForm = null;
        revealGoalId = null;
        confirmingRemovalId = null;
        refreshTimer.stop();
        Runnable hideTooltips = () -> { hideProgressTooltip(); hideLevelTooltip(); };
        if (SwingUtilities.isEventDispatchThread()) hideTooltips.run();
        else SwingUtilities.invokeLater(hideTooltips);
    }

    void clearGoalInteractionState()
    {
        SwingUtilities.invokeLater(() ->
        {
            confirmingRemovalId = null;
            closeGoalForm();
        });
    }

    void requestRefresh()
    {
        // EDT requests are serialized; stale client snapshots never replace newer ones.
        if (!SwingUtilities.isEventDispatchThread())
        {
            SwingUtilities.invokeLater(this::requestRefresh);
            return;
        }
        if (!running) return;
        final long generation = ++refreshGeneration;
        // RuneLite client state is read on the client thread, Swing updated on the EDT.
        plugin.runOnClientThread(() ->
        {
            List<GoalSnapshot> goals = new ArrayList<>();
            long now = plugin.goalClockNanos();
            plugin.expireGoalExpansions(now);
            
            List<GoalTracker.Goal> records = plugin.individualGoals();
            boolean noGoals = records.isEmpty();
            for (GoalTracker.Goal record : records)
            {
                if (record.skill == null)
                {
                    goals.add(new GoalSnapshot(record.id, null, 0, 0, false, 0, 0, 0,
                        0, 0, 0, 0, 0, false, false, false, null));
                    continue;
                }
                Skill skill = record.skill;
                int target = record.targetLevel;
                long targetXp = record.targetXp;
                if (targetXp <= 0) continue;
                int xp = plugin.getClient().getSkillExperience(skill);
                if (xp < 0) continue;
                long remaining = Math.max(0, (long) targetXp - xp);
                double progress = record.progress(xp);
                long rateEnd = record.completed ? record.completedAtNanos : now;
                long rate = record.ratePeriodStartNanos == 0 ? 0 : Math.round(record.rateXp * 3600.0 / Math.max(60.0, (rateEnd - record.ratePeriodStartNanos) / 1e9));
                long gained = record.sessionXp;
                boolean collapsed = record.expandedAtNanos == 0 || !ActiveGoalsPlugin.isWithinGoalCardExpansionWindow(record.expandedAtNanos, now, plugin.getGoalCardExpansionSeconds());
                double lapXp = skill == Skill.AGILITY ? plugin.agilityXpPerLap() : -1;
                boolean laps = lapXp > 0;
                long estimate = laps ? (long) Math.ceil(remaining / lapXp) : ActiveGoalsPlugin.estimateActionsLeft(remaining, record.samples);
                goals.add(new GoalSnapshot(record.id, skill, target, targetXp, target == 0, xp, record.startXp, record.startingLevel(), remaining, progress, rate, gained, estimate, laps, collapsed, record.completed, record.createdAtEpochMillis));
            }
            long sessionOverviewXp = plugin.getSessionOverviewTotalXp();
            long sessionSeconds = plugin.getSessionSeconds();
            boolean ready = plugin.goalDataReady();
            boolean profileLoading = plugin.goalProfileLoading();
            long actionSession = plugin.goalActionSession();
            SwingUtilities.invokeLater(() ->
            {
                if (!running || generation != refreshGeneration) return;
                if (!pressedButtons.isEmpty())
                {
                    refreshPending = true;
                    return;
                }
                refreshPending = false;
                renderDashboard(goals, sessionOverviewXp, sessionSeconds, ready, noGoals, actionSession, profileLoading);
            });
        });
    }

    private void applyPanelChange(long actionSession, Runnable action)
    {
        plugin.runOnClientThread(() ->
        {
            try
            {
                plugin.requireGoalAction(actionSession);
                plugin.expireGoalExpansions(plugin.goalClockNanos());
                action.run();
            }
            catch (IllegalStateException ignored) { }
            requestRefresh();
        });
    }

    private void renderDashboard(List<GoalSnapshot> goals, long sessionOverviewXp, long seconds, boolean ready, boolean noGoals, long actionSession, boolean profileLoading)
    {
        // The hovered goal survives card reconstruction; its new bar is bound below.
        hoveredProgressBar = null;
        hoveredLevelLabel = null;
        if (confirmingRemovalId != null && goals.stream().noneMatch(goal -> goal.id.equals(confirmingRemovalId)))
            confirmingRemovalId = null;
        JPanel reveal = goalForm != null && goalForm.getParent() == null ? goalForm : null;
        for (Component child : content.getComponents())
            if (child != goalForm) content.remove(child);
        JPanel goalHeader = new JPanel(new BorderLayout(5, 0));
        goalHeader.setOpaque(false);
        goalHeader.add(label("Goals: " + goals.size(), GOLD, Font.BOLD), BorderLayout.WEST);
        JButton add = new JButton("+");
        add.setFont(FontManager.getRunescapeBoldFont());
        add.setForeground(TEXT);
        add.setMargin(new Insets(1, 6, 1, 6));
        add.setFocusPainted(false);
        add.setToolTipText(ready ? "Add Goal" : "Log in and wait for skill data to load");
        add.setEnabled(ready && goalForm == null && confirmingRemovalId == null);
        add.addActionListener(e -> openGoalForm(null, actionSession));
        keepWhilePressed(add);
        goalHeader.add(add, BorderLayout.EAST);
        int index = 0;
        content.add(fullWidth(goalHeader), index++);
        if (!ready)
            content.add(label(profileLoading ? "Wait for your profile to load, then try again." : "Log in to add goals.", MUTED, Font.PLAIN), index++);
        else if (noGoals && goalForm == null)
            content.add(label("No goals yet. Select + to add one.", MUTED, Font.PLAIN), index++);
        content.add(Box.createVerticalStrut(8), index++);
        if (goalForm != null)
        {
            if (goalForm.getParent() != content) content.add(goalForm, index);
            index++;
            content.add(Box.createVerticalStrut(12), index);
        }
        for (GoalSnapshot goal : goals)
        {
            JPanel card = goalCard(goal, ready, actionSession);
            content.add(card);
            if (goal.id.equals(revealGoalId)) reveal = card;
            content.add(Box.createVerticalStrut(9));
        }
        revealGoalId = null;
        JPanel sessionCard = new JPanel();
        sessionCard.setLayout(new BoxLayout(sessionCard, BoxLayout.Y_AXIS));
        sessionCard.setBackground(CARD);
        sessionCard.setBorder(new EmptyBorder(10, 7, 10, 7));
        sessionCard.setAlignmentX(Component.LEFT_ALIGNMENT);
        sessionCard.add(label("Session Overview", GOLD, Font.BOLD));
        sessionCard.add(Box.createVerticalStrut(9));
        sessionCard.add(infoRow("Xp Gained:", fmt(sessionOverviewXp)));
        sessionCard.add(Box.createVerticalStrut(5));
        sessionCard.add(infoRow("Session Time:", duration(seconds)));
        sessionCard.setMaximumSize(new Dimension(Integer.MAX_VALUE, sessionCard.getPreferredSize().height));
        content.add(sessionCard);
        content.add(Box.createVerticalStrut(12));
        content.revalidate();
        content.repaint();
        SwingUtilities.invokeLater(() ->
        {
            if (hoveredProgressGoalId != null && hoveredProgressBar == null) hideProgressTooltip();
            else positionProgressTooltip();
            if (hoveredLevelGoalId != null && hoveredLevelLabel == null) hideLevelTooltip();
            else positionLevelTooltip();
        });
        if (reveal != null)
        {
            JPanel component = reveal;
            SwingUtilities.invokeLater(() ->
            {
                if (component.getParent() == content) content.scrollRectToVisible(component.getBounds());
            });
        }
    }

    private JPanel goalCard(GoalSnapshot goal, boolean ready, long actionSession)
    {
        boolean confirming = goal.id.equals(confirmingRemovalId);
        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(CARD);
        card.setBorder(new EmptyBorder(10, 7, 10, 7));
        card.setAlignmentX(Component.LEFT_ALIGNMENT);

        if (goal.skill == null)
        {
            card.add(label("Corrupted Save", GOLD, Font.BOLD));
            card.add(Box.createVerticalStrut(6));
            JPanel row = new JPanel(new BorderLayout(5, 0));
            row.setOpaque(false);
            row.add(label("This goal could not be loaded.", TEXT, Font.PLAIN), BorderLayout.CENTER);
            JButton delete = new JButton(trashIcon());
            delete.setToolTipText("Delete");
            delete.getAccessibleContext().setAccessibleName("Delete");
            delete.setMargin(new Insets(0, 5, 0, 5));
            delete.setEnabled(ready);
            delete.addActionListener(e -> applyPanelChange(actionSession, () -> plugin.removeIndividualGoal(goal.id)));
            keepWhilePressed(delete);
            row.add(delete, BorderLayout.EAST);
            card.add(fullWidth(row));
            card.setMaximumSize(new Dimension(Integer.MAX_VALUE, card.getPreferredSize().height));
            return card;
        }

        JPanel header = new JPanel(new BorderLayout(5, 0));
        header.setOpaque(false);
        JLabel skill = label(goal.skill.getName() + (goal.completed ? " — Completed" : ""), TEXT, Font.BOLD);
        try
        {
            java.awt.image.BufferedImage img = icons.getSkillImage(goal.skill);
            if (img != null) skill.setIcon(new ImageIcon(img));
        }
        catch (RuntimeException ignored) { }
        header.add(skill, BorderLayout.CENTER);
        JPanel controls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 2, 0));
        controls.setOpaque(false);
        if (!goal.completed)
        {
            JButton edit = new JButton(pencilIcon());
            edit.setToolTipText("Edit Goal");
            edit.getAccessibleContext().setAccessibleName("Edit Goal");
            edit.setMargin(new Insets(0, 5, 0, 5));
            edit.setEnabled(ready && goalForm == null && confirmingRemovalId == null);
            edit.addActionListener(e -> openGoalForm(goal, actionSession));
            keepWhilePressed(edit);
            controls.add(edit);
        }
        JButton clear = new JButton(trashIcon());
        clear.setToolTipText("Remove Goal");
        clear.getAccessibleContext().setAccessibleName("Remove Goal");
        clear.setMargin(new Insets(0, 5, 0, 5));
        clear.setEnabled(ready && goalForm == null && confirmingRemovalId == null);
        clear.addActionListener(e -> showRemoveGoalConfirmation(goal));
        keepWhilePressed(clear);
        controls.add(clear);
        JButton collapse = new JButton("−");
        collapse.setToolTipText(goal.collapsed ? "Expand goal" : "Collapse goal");
        collapse.getAccessibleContext().setAccessibleName(goal.collapsed ? "Expand goal" : "Collapse goal");
        collapse.setMargin(new Insets(0, 4, 0, 4));
        collapse.setEnabled(!confirming);
        collapse.addActionListener(e -> applyPanelChange(actionSession, () ->
            plugin.setIndividualGoalCollapsed(goal.id, !goal.collapsed)));
        keepWhilePressed(collapse);
        controls.add(collapse);
        header.add(controls, BorderLayout.EAST);
        applyRuneLiteFont(controls);
        card.add(fullWidth(header));
        if (confirming)
        {
            card.add(Box.createVerticalStrut(6));
            String target = goal.hasCustomXpTarget ? "Xp Target: " + fmt(goal.targetXp)
                : levelGoalText(goal);
            card.add(goalTargetLabel(goal, target));
            card.add(Box.createVerticalStrut(7));
            card.add(label("Remove this goal?", TEXT, Font.PLAIN));
            card.add(Box.createVerticalStrut(7));
            JPanel buttons = new JPanel(new GridLayout(1, 2, 4, 0));
            buttons.setOpaque(false);
            JButton remove = new JButton("Remove Goal");
            remove.setEnabled(ready);
            remove.addActionListener(e ->
            {
                confirmingRemovalId = null;
                applyPanelChange(actionSession, () -> plugin.removeIndividualGoal(goal.id));
            });
            JButton cancel = new JButton("Cancel");
            cancel.addActionListener(e -> { confirmingRemovalId = null; requestRefresh(); });
            keepWhilePressed(remove);
            keepWhilePressed(cancel);
            buttons.add(remove);
            buttons.add(cancel);
            applyRuneLiteFont(buttons);
            card.add(fullWidth(buttons));
            card.setMaximumSize(new Dimension(Integer.MAX_VALUE, card.getPreferredSize().height));
            return card;
        }
        if (goal.collapsed)
        {
            card.add(Box.createVerticalStrut(6));
            String endpoints = goal.hasCustomXpTarget
                ? "Xp: " + fmt(goal.startingXp) + " → " + fmt(goal.targetXp)
                : levelGoalText(goal);
            card.add(goalTargetLabel(goal, endpoints));
            card.add(Box.createVerticalStrut(5));
            card.add(infoRow("Goal Progress:", String.format("%.1f%%", goal.progress * 100)));
            card.add(Box.createVerticalStrut(5));
            card.add(goalProgressBar(goal));
            Dimension preferred = card.getPreferredSize();
            card.setMaximumSize(new Dimension(Integer.MAX_VALUE, preferred.height));
            return card;
        }
        card.add(Box.createVerticalStrut(6));
        card.add(goalTargetLabel(goal, goal.hasCustomXpTarget ? "Xp Target: " + fmt(goal.targetXp) :
            levelGoalText(goal)));
        card.add(Box.createVerticalStrut(4));
        card.add(infoRow("Goal Progress:", String.format("%.1f%%", goal.progress * 100)));
        card.add(Box.createVerticalStrut(5));
        card.add(goalProgressBar(goal));
        card.add(Box.createVerticalStrut(9));
        JSeparator divider = new JSeparator();
        divider.setForeground(new Color(81, 72, 58));
        divider.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        card.add(fullWidth(divider));
        card.add(Box.createVerticalStrut(5));
        card.add(infoRow("Created:", formatCreationTime(goal.createdAtEpochMillis)));
        card.add(Box.createVerticalStrut(5));
        card.add(infoRow("Current Xp:", fmt(goal.currentXp)));
        card.add(Box.createVerticalStrut(5));
        card.add(infoRow("Xp Remaining:", fmt(goal.remainingXp)));
        card.add(Box.createVerticalStrut(5));
        card.add(infoRow("Xp/hour:", goal.xpPerHour > 0 ? fmt(goal.xpPerHour) : "Waiting for Xp"));
        card.add(Box.createVerticalStrut(5));
        card.add(infoRow(goal.laps ? "Laps Left:" : "Actions Left:", goal.actionsLeft < 0 ? "Waiting for Xp" : fmt(goal.actionsLeft)));
        card.add(Box.createVerticalStrut(5));
        card.add(infoRow("Session Xp:", fmt(goal.goalCardSessionXp)));
        card.add(Box.createVerticalStrut(5));
        String eta = goal.xpPerHour > 0 ? duration((long) Math.ceil(goal.remainingXp * 3600.0 / goal.xpPerHour)) : "—";
        card.add(infoRow("ETA:", eta));
        Dimension preferred = card.getPreferredSize();
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, preferred.height));
        return card;
    }

    private JLabel goalTargetLabel(GoalSnapshot goal, String text)
    {
        JLabel result = label(text, GOLD, Font.PLAIN);
        if (goal.hasCustomXpTarget || goal.completed) return result;
        String tip = "Current Level: " + ActiveGoalsPlugin.virtualLevelForXp(Math.max(0, goal.currentXp));
        result.setToolTipText(tip);
        result.getAccessibleContext().setAccessibleDescription(tip);
        // Rebind the persistent overlay when periodic refreshes rebuild the label.
        ToolTipManager.sharedInstance().unregisterComponent(result);
        if (goal.id.equals(hoveredLevelGoalId))
        {
            hoveredLevelLabel = result;
            levelTooltip.setComponent(result);
            levelTooltip.setTipText(tip);
        }
        result.addMouseListener(new MouseAdapter()
        {
            @Override public void mouseEntered(MouseEvent e)
            {
                if (!goal.id.equals(hoveredLevelGoalId))
                {
                    hideProgressTooltip();
                    hideLevelTooltip();
                    hoveredLevelGoalId = goal.id;
                    hoveredLevelLabel = result;
                    levelTooltipDelay.restart();
                }
            }

            @Override public void mouseExited(MouseEvent e)
            {
                if (hoveredLevelLabel == result) hideLevelTooltip();
            }
        });
        return result;
    }

    private void showLevelTooltip()
    {
        if (!running || hoveredLevelLabel == null || !hoveredLevelLabel.isShowing()) return;
        JRootPane root = SwingUtilities.getRootPane(hoveredLevelLabel);
        if (root == null) return;
        levelTooltip.setComponent(hoveredLevelLabel);
        levelTooltip.setTipText(hoveredLevelLabel.getToolTipText());
        root.getLayeredPane().add(levelTooltip, JLayeredPane.POPUP_LAYER);
        levelTooltip.setVisible(true);
        positionLevelTooltip();
    }

    private void positionLevelTooltip()
    {
        if (hoveredLevelLabel == null || !hoveredLevelLabel.isShowing() || !levelTooltip.isVisible()
            || !(levelTooltip.getParent() instanceof JLayeredPane)) return;
        JLayeredPane layer = (JLayeredPane) levelTooltip.getParent();
        JViewport viewport = (JViewport) SwingUtilities.getAncestorOfClass(JViewport.class, hoveredLevelLabel);
        if (viewport == null || !viewport.getViewRect().intersects(
            SwingUtilities.convertRectangle(hoveredLevelLabel.getParent(), hoveredLevelLabel.getBounds(), viewport.getView())))
        {
            hideLevelTooltip();
            return;
        }
        Rectangle bounds = SwingUtilities.convertRectangle(hoveredLevelLabel.getParent(), hoveredLevelLabel.getBounds(), layer);
        Dimension tip = levelTooltip.getPreferredSize();
        int x = Math.max(0, Math.min(layer.getWidth() - tip.width, bounds.x + (bounds.width - tip.width) / 2));
        int y = bounds.y + bounds.height;
        if (y + tip.height > layer.getHeight()) y = bounds.y - tip.height;
        y = Math.max(0, Math.min(layer.getHeight() - tip.height, y));
        levelTooltip.setBounds(x, y, tip.width, tip.height);
        levelTooltip.repaint();
    }

    private void hideLevelTooltip()
    {
        levelTooltipDelay.stop();
        hoveredLevelGoalId = null;
        hoveredLevelLabel = null;
        levelTooltip.setVisible(false);
        Container parent = levelTooltip.getParent();
        if (parent != null)
        {
            parent.remove(levelTooltip);
            parent.repaint();
        }
    }

    /** A compact, width-constrained statistics row. */
    private JComponent goalProgressBar(GoalSnapshot goal)
    {
        JProgressBar bar = new JProgressBar(0, 1000);
        bar.setValue((int) (goal.progress * 1000));
        long total = Math.max(0, goal.targetXp - goal.startingXp);
        long gained = Math.max(0, Math.min(total, (long) goal.currentXp - goal.startingXp));
        bar.setToolTipText(fmt(gained) + " / " + fmt(total) + " XP");
        // A persistent overlay is used instead of Swing's pointer-positioned popup.
        ToolTipManager.sharedInstance().unregisterComponent(bar);
        if (goal.id.equals(hoveredProgressGoalId))
        {
            hoveredProgressBar = bar;
            progressTooltip.setComponent(bar);
            progressTooltip.setTipText(bar.getToolTipText());
        }
        bar.addMouseListener(new MouseAdapter()
        {
            @Override public void mouseEntered(MouseEvent e)
            {
                if (!goal.id.equals(hoveredProgressGoalId))
                {
                    hideLevelTooltip();
                    hideProgressTooltip();
                    hoveredProgressGoalId = goal.id;
                    hoveredProgressBar = bar;
                    progressTooltipDelay.restart();
                }
            }

            @Override public void mouseExited(MouseEvent e)
            {
                if (hoveredProgressBar == bar) hideProgressTooltip();
            }
        });
        bar.setForeground(new Color(230, 180, 79));
        bar.setBackground(new Color(23, 20, 15));
        bar.setPreferredSize(new Dimension(200, 10));
        bar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 10));
        return fullWidth(bar);
    }

    private void showProgressTooltip()
    {
        if (!running || hoveredProgressBar == null || !hoveredProgressBar.isShowing()) return;
        JRootPane root = SwingUtilities.getRootPane(hoveredProgressBar);
        if (root == null) return;
        progressTooltip.setComponent(hoveredProgressBar);
        progressTooltip.setTipText(hoveredProgressBar.getToolTipText());
        root.getLayeredPane().add(progressTooltip, JLayeredPane.POPUP_LAYER);
        progressTooltip.setVisible(true);
        positionProgressTooltip();
    }

    private void positionProgressTooltip()
    {
        if (hoveredProgressBar == null || !hoveredProgressBar.isShowing() || !progressTooltip.isVisible()
            || !(progressTooltip.getParent() instanceof JLayeredPane)) return;
        JLayeredPane layer = (JLayeredPane) progressTooltip.getParent();
        JViewport viewport = (JViewport) SwingUtilities.getAncestorOfClass(JViewport.class, hoveredProgressBar);
        if (viewport == null || !viewport.getViewRect().intersects(
            SwingUtilities.convertRectangle(hoveredProgressBar.getParent(), hoveredProgressBar.getBounds(), viewport.getView())))
        {
            hideProgressTooltip();
            return;
        }
        Rectangle bar = SwingUtilities.convertRectangle(hoveredProgressBar.getParent(), hoveredProgressBar.getBounds(), layer);
        Dimension tip = progressTooltip.getPreferredSize();
        int x = Math.max(0, Math.min(layer.getWidth() - tip.width, bar.x + (bar.width - tip.width) / 2));
        int y = bar.y + bar.height;
        if (y + tip.height > layer.getHeight()) y = bar.y - tip.height;
        y = Math.max(0, Math.min(layer.getHeight() - tip.height, y));
        progressTooltip.setBounds(x, y, tip.width, tip.height);
        progressTooltip.repaint();
    }

    private void hideProgressTooltip()
    {
        progressTooltipDelay.stop();
        hoveredProgressGoalId = null;
        hoveredProgressBar = null;
        progressTooltip.setVisible(false);
        Container parent = progressTooltip.getParent();
        if (parent != null)
        {
            parent.remove(progressTooltip);
            parent.repaint();
        }
    }

    private JPanel infoRow(String key, String value)
    {
        JPanel row = new JPanel(new BorderLayout(3, 0));
        row.setOpaque(false);
        JLabel left = label(key, MUTED, Font.PLAIN);
        JLabel right = label(value, MUTED, Font.PLAIN);
        right.setHorizontalAlignment(SwingConstants.RIGHT);
        row.add(left, BorderLayout.WEST);
        row.add(right, BorderLayout.CENTER);
        return fullWidth(row);
    }

    private static JPanel fullWidth(JPanel panel)
    {
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        Dimension size = panel.getPreferredSize();
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, size.height));
        return panel;
    }

    private static JComponent fullWidth(JComponent component)
    {
        component.setAlignmentX(Component.LEFT_ALIGNMENT);
        return component;
    }

    private static JPanel centered(JLabel label)
    {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        row.setOpaque(false);
        row.add(label);
        return fullWidth(row);
    }

    private void keepWhilePressed(JButton button)
    {
        button.setRolloverEnabled(true);
        button.getModel().addChangeListener(e ->
        {
            if (button.getModel().isPressed())
            {
                pressedButtons.add(button);
            }
            else if (pressedButtons.remove(button) && pressedButtons.isEmpty() && refreshPending)
            {
                refreshPending = false;
                requestRefresh();
            }
        });
    }

    private void configureXpTarget(Skill skill)
    {
        String value = ActiveGoalsDialogs.promptText(this,
            "Enter an exact Xp goal (1–200,000,000) for " + skill.getName() + ":",
            "Set " + skill.getName() + " Xp Target",
            plugin.hasCustomXpTarget(skill) ? String.valueOf(plugin.getTargetXp(skill)) : "");
        if (value == null) return;
        try
        {
            long xp = Long.parseLong(value.trim().replace(",", ""));
            if (xp < 1 || xp > 200_000_000L) throw new NumberFormatException();
            applyPanelChange(plugin.goalActionSession(), () ->
            {
                try
                {
                    plugin.setXpTarget(skill, xp);
                }
                catch (IllegalArgumentException ex)
                {
                    SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this,
                        "Target must be above your current Xp and the goal must not be completed.",
                        "Invalid Xp Target", JOptionPane.WARNING_MESSAGE));
                }
            });
        }
        catch (NumberFormatException ex)
        {
            JOptionPane.showMessageDialog(this, "Enter a valid Xp amount from 1 to 200,000,000.",
                "Invalid Xp Target", JOptionPane.WARNING_MESSAGE);
        }
    }

    private void chooseSkill()
    {
        List<SkillChoice> skills = new ArrayList<>();
        SkillChoice initial = null;
        for (Skill skill : Skill.values())
        {
            if (skill == Skill.OVERALL) continue;
            SkillChoice option = new SkillChoice(skill);
            skills.add(option);
            if (skill == Skill.AGILITY) initial = option;
        }
        skills.sort(Comparator.comparing(choice -> choice.skill.getName(), String.CASE_INSENSITIVE_ORDER));
        SkillChoice chosen = ActiveGoalsDialogs.choose(this, "Choose a skill:",
            "Add Active Goals Goal", skills.toArray(new SkillChoice[0]), initial);
        if (chosen != null) plugin.showLevelTargetDialog(chosen.skill);
    }

    private void openGoalForm(GoalSnapshot goal, long actionSession)
    {
        if (goalForm != null || confirmingRemovalId != null) return;
        goalForm = createGoalForm(goal, actionSession);
        requestRefresh();
    }

    private void closeGoalForm()
    {
        goalForm = null;
        requestRefresh();
    }

    private JPanel createGoalForm(GoalSnapshot goal, long actionSession)
    {
        JPanel form = new JPanel();
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        form.setBackground(CARD);
        form.setBorder(new EmptyBorder(10, 7, 10, 7));
        form.setAlignmentX(Component.LEFT_ALIGNMENT);
        form.add(label(goal == null ? "＋ Add goal" : "Edit " + goal.skill.getName() + " goal", GOLD, Font.BOLD));
        form.add(Box.createVerticalStrut(7));
        JLabel current = label(goal == null ? "Choose a skill" : currentLevelText(goal.currentXp), MUTED, Font.PLAIN);
        JComboBox<String> mode = new JComboBox<>(new String[]{"Level", "Xp"});
        JTextField input = new JTextField();
        String[] drafts = {"", ""};
        JLabel error = label(" ", MUTED, Font.PLAIN);
        JComboBox<Skill> choices = goal == null ? createSkillChoices() : null;
        if (choices != null)
        {
            form.add(choices);
            form.add(Box.createVerticalStrut(5));
            choices.addActionListener(e ->
            {
                Skill skill = (Skill) choices.getSelectedItem();
                drafts[0] = "";
                drafts[1] = "";
                input.setText("");
                error.setText(" ");
                current.setText("Choose a skill");
                if (skill == null) return;
                plugin.runOnClientThread(() ->
                {
                    int xp = plugin.getClient().getSkillExperience(skill);
                    SwingUtilities.invokeLater(() ->
                    {
                        if (choices.getSelectedItem() == skill) current.setText(currentLevelText(xp));
                    });
                });
            });
        }
        else
        {
            mode.setSelectedIndex(goal.hasCustomXpTarget ? 1 : 0);
            input.setText(goal.hasCustomXpTarget ? fmt(goal.targetXp) : Integer.toString(goal.targetLevel));
        }
        form.add(current);
        form.add(Box.createVerticalStrut(7));
        mode.addItemListener(e ->
        {
            int index = "Xp".equals(e.getItem()) ? 1 : 0;
            if (e.getStateChange() == ItemEvent.DESELECTED) drafts[index] = input.getText();
            else
            {
                input.setText(drafts[index]);
                error.setText(" ");
            }
        });
        JPanel row = new JPanel(new BorderLayout(4, 0));
        row.add(mode, BorderLayout.WEST);
        row.add(input, BorderLayout.CENTER);
        row.setOpaque(false);
        form.add(fullWidth(row));
        form.add(Box.createVerticalStrut(4));
        form.add(error);
        JPanel buttons = new JPanel(new GridLayout(1, 2, 4, 0));
        JButton cancel = new JButton("Cancel");
        cancel.addActionListener(e -> closeGoalForm());
        JButton submit = new JButton(goal == null ? "Create Goal" : "Save Changes");
        submit.addActionListener(e ->
        {
            Skill skill = choices == null ? goal.skill : (Skill) choices.getSelectedItem();
            if (skill == null) { error.setText("Choose a skill."); return; }
            boolean isXp = mode.getSelectedIndex() == 1;
            final long amount;
            try { amount = Long.parseLong(input.getText().trim().replace(",", "")); }
            catch (NumberFormatException ex) { error.setText("Enter a valid target."); return; }
            if (isXp ? amount < 1 || amount > 200_000_000L : amount < 2 || amount > 126)
            { error.setText(isXp ? "Enter Xp from 1 to 200,000,000." : "Enter a level from 2 to 126."); return; }

            int targetLevel = isXp ? 0 : (int) amount;
            long targetXp = isXp ? amount : ActiveGoalsPlugin.xpForLevel(targetLevel);
            submit.setEnabled(false);
            cancel.setEnabled(false);
            plugin.runOnClientThread(() ->
            {
                try
                {
                    plugin.requireGoalAction(actionSession);
                    if (goal == null) plugin.addIndividualGoal(skill, targetLevel, targetXp);
                    else plugin.editIndividualGoal(goal.id, targetLevel, targetXp);
                    SwingUtilities.invokeLater(() ->
                    {
                        if (goalForm != form) return;
                        if (goal != null) revealGoalId = goal.id;
                        closeGoalForm();
                    });
                }
                catch (IllegalArgumentException | IllegalStateException ex)
                {
                    SwingUtilities.invokeLater(() ->
                    {
                        if (goalForm != form || actionSession != plugin.goalActionSession()) return;
                        error.setText(ex.getMessage());
                        submit.setEnabled(true);
                        cancel.setEnabled(true);
                    });
                }
            });
        });
        cancel.setMargin(new Insets(5, 4, 5, 4));
        submit.setMargin(new Insets(5, 4, 5, 4));
        buttons.setOpaque(false);
        buttons.add(submit);
        buttons.add(cancel);
        form.add(Box.createVerticalStrut(7));
        form.add(fullWidth(buttons));
        applyRuneLiteFont(form);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
        buttons.setMaximumSize(new Dimension(Integer.MAX_VALUE, buttons.getPreferredSize().height));
        form.setMaximumSize(new Dimension(Integer.MAX_VALUE, form.getPreferredSize().height));
        return form;
    }

    private JComboBox<Skill> createSkillChoices()
    {
        List<Skill> available = new ArrayList<>();
        for (Skill skill : Skill.values()) if (skill != Skill.OVERALL) available.add(skill);
        available.sort(Comparator.comparing(Skill::getName, String.CASE_INSENSITIVE_ORDER));
        JComboBox<Skill> choices = new JComboBox<>(available.toArray(new Skill[0]));
        choices.setFont(FontManager.getRunescapeFont());
        choices.setFont(FontManager.getRunescapeFont());
        choices.setSelectedItem(null);
        choices.setRenderer(new DefaultListCellRenderer()
        {
            @Override public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected, boolean focus)
            {
                JLabel item = (JLabel) super.getListCellRendererComponent(list, value, index, selected, focus);
                item.setFont(FontManager.getRunescapeFont());
                if (value instanceof Skill)
                {
                    Skill skill = (Skill) value;
                    item.setText(skill.getName());
                    java.awt.image.BufferedImage icon = icons.getSkillImage(skill);
                    if (icon != null) item.setIcon(new ImageIcon(icon));
                }
                else item.setText("Select a skill…");
                return item;
            }
        });
        choices.setAlignmentX(Component.LEFT_ALIGNMENT);
        choices.setMaximumSize(new Dimension(Integer.MAX_VALUE, choices.getPreferredSize().height));
        return choices;
    }

    private static String levelGoalText(GoalSnapshot goal)
    {
        return "Level: " + goal.startingLevel + " → " + goal.targetLevel;
    }

    private static String currentLevelText(int xp)
    {
        return "Current level: " + ActiveGoalsPlugin.virtualLevelForXp(Math.max(0, xp));
    }

    /** Display-friendly wrapper; never changes RuneLite's Skill enum identifiers. */
    private static final class SkillChoice
    {
        private final Skill skill;

        private SkillChoice(Skill skill) { this.skill = skill; }

        @Override
        public String toString() { return skill.getName(); }
    }

    /** Keep goal cards within the viewport instead of letting their preferred width clip the sidebar. */
    private static final class ViewportWidthPanel extends JPanel implements Scrollable
    {
        @Override
        public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }

        @Override
        public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) { return 16; }

        @Override
        public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction)
        {
            return Math.max(16, visibleRect.height - 32);
        }

        @Override
        public boolean getScrollableTracksViewportWidth() { return true; }

        @Override
        public boolean getScrollableTracksViewportHeight() { return false; }
    }

    /** Subtle dark scrollbar matching RuneLite's sidebar rather than the system's bright scrollbar. */
    private static final class ActiveGoalsScrollBarUI extends BasicScrollBarUI
    {
        private static final Color TRACK = new Color(29, 27, 24);
        private static final Color THUMB = new Color(120, 105, 78);

        @Override
        protected void configureScrollBarColors()
        {
            trackColor = TRACK;
            thumbColor = THUMB;
            thumbDarkShadowColor = THUMB;
            thumbHighlightColor = THUMB;
            thumbLightShadowColor = THUMB;
        }

        @Override
        protected JButton createDecreaseButton(int orientation) { return hiddenButton(); }

        @Override
        protected JButton createIncreaseButton(int orientation) { return hiddenButton(); }

        private JButton hiddenButton()
        {
            JButton button = new JButton();
            button.setPreferredSize(new Dimension(0, 0));
            button.setMinimumSize(new Dimension(0, 0));
            button.setMaximumSize(new Dimension(0, 0));
            return button;
        }

        @Override
        protected void paintThumb(Graphics g, JComponent c, Rectangle bounds)
        {
            if (bounds.isEmpty() || !scrollbar.isEnabled()) return;
            g.setColor(THUMB);
            g.fillRoundRect(bounds.x + 1, bounds.y, Math.max(3, bounds.width - 2), bounds.height, 5, 5);
        }

        @Override
        protected void paintTrack(Graphics g, JComponent c, Rectangle bounds)
        {
            g.setColor(TRACK);
            g.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);
        }
    }

    private static JLabel label(String value, Color color, int style)
    {
        JLabel l = new JLabel(value);
        l.setForeground(color);
        l.setFont(style == Font.BOLD ? FontManager.getRunescapeBoldFont() : FontManager.getRunescapeFont());
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        return l;
    }

    private static Icon journalIcon()
    {
        return new Icon()
        {
            @Override public int getIconWidth() { return 16; }
            @Override public int getIconHeight() { return 16; }
            @Override public void paintIcon(Component component, Graphics graphics, int x, int y)
            {
                Graphics2D g = (Graphics2D) graphics.create();
                try
                {
                    g.translate(x, y);
                    g.setColor(new Color(223, 187, 117));
                    g.drawRoundRect(3, 1, 10, 13, 3, 3);
                    g.drawLine(5, 4, 10, 4);
                    g.drawLine(5, 7, 10, 7);
                    g.drawLine(5, 10, 8, 10);
                    g.drawArc(0, 1, 6, 5, 90, 180);
                    g.drawArc(10, 10, 5, 4, 270, 180);
                }
                finally { g.dispose(); }
            }
        };
    }

    private static Icon trashIcon()
    {
        return new Icon()
        {
            @Override public int getIconWidth() { return 12; }
            @Override public int getIconHeight() { return 14; }
            @Override public void paintIcon(Component component, Graphics graphics, int x, int y)
            {
                Graphics2D g = (Graphics2D) graphics.create();
                try
                {
                    g.setColor(component.isEnabled() ? component.getForeground() : MUTED);
                    g.drawLine(x + 1, y + 3, x + 10, y + 3);
                    g.drawLine(x + 4, y + 1, x + 7, y + 1);
                    g.drawRect(x + 2, y + 4, 7, 8);
                    g.drawLine(x + 4, y + 6, x + 4, y + 10);
                    g.drawLine(x + 7, y + 6, x + 7, y + 10);
                }
                finally { g.dispose(); }
            }
        };
    }

    private static Icon pencilIcon()
    {
        return new Icon()
        {
            @Override public int getIconWidth() { return 12; }
            @Override public int getIconHeight() { return 14; }
            @Override public void paintIcon(Component component, Graphics graphics, int x, int y)
            {
                Graphics2D g = (Graphics2D) graphics.create();
                try
                {
                    g.translate(x, y);
                    g.setColor(component.isEnabled() ? component.getForeground() : MUTED);
                    g.drawPolygon(new int[]{1, 2, 9, 11, 4}, new int[]{12, 8, 1, 3, 10}, 5);
                    g.drawLine(7, 3, 9, 5);
                    g.drawLine(2, 8, 4, 10);
                }
                finally { g.dispose(); }
            }
        };
    }

    private static void applyRuneLiteFont(Component component)
    {
        if (component instanceof AbstractButton || component instanceof JTextField || component instanceof JComboBox)
        {
            component.setFont(FontManager.getRunescapeFont());
        }
        if (component instanceof Container)
            for (Component child : ((Container) component).getComponents()) applyRuneLiteFont(child);
    }

    private static String fmt(long n) { return String.format(Locale.US, "%,d", n); }
    private static String formatCreationTime(Long epochMillis)
    {
        return epochMillis == null ? "Unknown" : CREATION_TIME_FORMAT.format(
            Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()));
    }
    private static String duration(long sec)
    {
        long days = sec / 86400, hours = sec % 86400 / 3600, minutes = sec % 3600 / 60;
        if (days > 0) return days + " days, " + hours + " hours";
        if (hours > 0) return hours + " hours, " + minutes + " minutes";
        return minutes + " minutes";
    }

    private static final class GoalSnapshot
    {
        final String id;
        final Skill skill;
        final int targetLevel;
        final long targetXp;
        final boolean hasCustomXpTarget;
        final int currentXp;
        final int startingXp;
        final int startingLevel;
        final long remainingXp;
        final double progress;
        final long xpPerHour;
        final long goalCardSessionXp;
        final long actionsLeft;
        final boolean laps;
        final boolean collapsed;
        final boolean completed;
        final Long createdAtEpochMillis;

        GoalSnapshot(String id, Skill skill, int targetLevel, long targetXp, boolean hasCustomXpTarget,
             int currentXp, int startingXp, int startingLevel, long remainingXp, double progress,
             long xpPerHour, long goalCardSessionXp, long actionsLeft, boolean laps, boolean collapsed, boolean completed,
             Long createdAtEpochMillis)
        {
            this.id = id;
            this.skill = skill;
            this.targetLevel = targetLevel;
            this.targetXp = targetXp;
            this.hasCustomXpTarget = hasCustomXpTarget;
            this.currentXp = currentXp;
            this.startingXp = startingXp;
            this.startingLevel = startingLevel;
            this.remainingXp = remainingXp;
            this.progress = progress;
            this.xpPerHour = xpPerHour;
            this.goalCardSessionXp = goalCardSessionXp;
            this.actionsLeft = actionsLeft;
            this.laps = laps;
            this.collapsed = collapsed;
            this.completed = completed;
            this.createdAtEpochMillis = createdAtEpochMillis;
        }
    }
}
