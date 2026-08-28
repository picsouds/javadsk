package dsk.gui.view;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.Component;
import java.awt.GridBagConstraints;

/** Une ligne "label + champ [+ bouton]" dans un formulaire GridBagLayout, partagée par les boîtes de dialogue. */
final class DialogRows {

    private DialogRows() {
    }

    static void add(JPanel panel, GridBagConstraints gbc, int row, String label, Component field, JButton button) {
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.weightx = 0;
        gbc.fill = GridBagConstraints.NONE;
        panel.add(new JLabel(label), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        panel.add(field, gbc);

        if (button != null) {
            gbc.gridx = 2;
            gbc.weightx = 0;
            gbc.fill = GridBagConstraints.NONE;
            panel.add(button, gbc);
        }
    }
}
