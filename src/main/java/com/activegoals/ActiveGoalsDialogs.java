package com.activegoals;

import java.awt.Component;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.JLabel;
import javax.swing.BoxLayout;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.FontManager;

/** Consistent dialogs: affirmative action on the left, focused/default Cancel on the right. */
final class ActiveGoalsDialogs
{
    private ActiveGoalsDialogs() { }

    private static boolean confirm(Component parent, Object message, String title,
                                   String affirmative, int messageType)
    {
        JButton accept = new JButton(affirmative);
        JButton cancel = new JButton("Cancel");
        accept.setFont(FontManager.getRunescapeFont());
        cancel.setFont(FontManager.getRunescapeFont());
        Object[] options = {accept, cancel};
        JOptionPane pane = new JOptionPane(message, messageType,
            JOptionPane.DEFAULT_OPTION, null, options, cancel);
        JDialog dialog = pane.createDialog(parent, title);
        dialog.getRootPane().setDefaultButton(cancel);
        dialog.addWindowListener(new WindowAdapter()
        {
            @Override
            public void windowOpened(WindowEvent e)
            {
                cancel.requestFocusInWindow();
            }
        });
        accept.addActionListener(e -> { pane.setValue(accept); dialog.dispose(); });
        cancel.addActionListener(e -> { pane.setValue(cancel); dialog.dispose(); });
        dialog.setVisible(true);
        boolean confirmed = pane.getValue() == accept;
        dialog.dispose();
        return confirmed;
    }

    static boolean confirmAction(Component parent, Object message, String title, String action)
    {
        return confirm(parent, message, title, action, JOptionPane.WARNING_MESSAGE);
    }

    static String promptText(Component parent, String message, String title, String initial)
    {
        JTextField field = new JTextField(initial == null ? "" : initial, 18);
        field.setFont(FontManager.getRunescapeFont());
        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        JLabel messageLabel = new JLabel(message);
        messageLabel.setFont(FontManager.getRunescapeFont());
        content.add(messageLabel);
        content.add(field);
        content.setBorder(new EmptyBorder(4, 0, 4, 0));
        return confirm(parent, content, title, "OK", JOptionPane.QUESTION_MESSAGE)
            ? field.getText() : null;
    }

    static <T> T choose(Component parent, String message, String title, T[] values, T initial)
    {
        JComboBox<T> choices = new JComboBox<>(values);
        choices.setFont(FontManager.getRunescapeFont());
        choices.setSelectedItem(initial);
        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        JLabel messageLabel = new JLabel(message);
        messageLabel.setFont(FontManager.getRunescapeFont());
        content.add(messageLabel);
        content.add(choices);
        return confirm(parent, content, title, "OK", JOptionPane.QUESTION_MESSAGE)
            ? (T) choices.getSelectedItem() : null;
    }
}
