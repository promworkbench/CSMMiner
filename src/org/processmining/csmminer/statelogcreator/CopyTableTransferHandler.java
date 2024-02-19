package org.processmining.csmminer.statelogcreator;

import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.beans.PropertyEditor;
import java.beans.PropertyEditorManager;
import java.io.IOException;

import javax.swing.JComponent;
import javax.swing.JTable;
import javax.swing.TransferHandler;

/**
 * @author Neil Weber
 */
public class CopyTableTransferHandler extends TransferHandler {
	protected Transferable createTransferable(JComponent c)
    {
        if (c instanceof JTable == false)
            return null;

        JTable table = (JTable) c;
        int[] rows = table.getSelectedRows();
        int[] columns = table.getSelectedColumns();
        if (rows == null || columns == null)
            return null;
        
        Object value;
        if (rows.length != 1 || columns.length != 1) {
        	int row = table.getSelectionModel().getLeadSelectionIndex();
            int column = table.getColumnModel().getSelectionModel().getLeadSelectionIndex();
        	value = table.getValueAt(row, column);
        }
        else {
        	value = table.getValueAt(rows[0], columns[0]);
        }

        if (value == null)
            return null;

        return new CopyTransferable(value);
    }


    public int getSourceActions(JComponent c)
    {
        return COPY;
    }


    public boolean importData(JComponent comp, Transferable transferable)
    {
        if (comp instanceof JTable == false)
            return false;

        JTable table = (JTable) comp;

        if (importCellData(table, transferable))
            return true;

        return false;
    }


    protected boolean importCellData(JTable table, Transferable transferable)
    {
        int[] rows = table.getSelectedRows();
        int[] columns = table.getSelectedColumns();
        if (rows == null || columns == null || rows.length != 1 || columns.length != 1)
            return false;

        int rowIndex = rows[0];
        int columnIndex = columns[0];

        if (table.isCellEditable(rowIndex, columnIndex) == false)
            return false;

        if (importCellObject(table, rowIndex, columnIndex, transferable))
            return true;


        Class valueClass = table.getColumnClass(columnIndex);
        PropertyEditor editor = PropertyEditorManager.findEditor(valueClass);
        DataFlavor stringFlavor = getStringFlavor(transferable);
        if (editor == null || stringFlavor == null)
            return false;

        try
        {
            System.out.println("Converting String to " + valueClass.getSimpleName());
            editor.setAsText((String) transferable.getTransferData(stringFlavor));
            setCellValue(table, rowIndex, columnIndex, editor.getValue());
            return true;
        }
        catch (UnsupportedFlavorException e)
        {
        }
        catch (IOException e)
        {
        }
        catch (IllegalArgumentException e)
        {
            Toolkit.getDefaultToolkit().beep();
        }

        return false;
    }



    protected DataFlavor getStringFlavor(Transferable transferable)
    {
        if (transferable.isDataFlavorSupported(DataFlavor.stringFlavor))
            return DataFlavor.stringFlavor;

        DataFlavor[] flavors = transferable.getTransferDataFlavors();
        for (DataFlavor flavor : flavors)
        {
            if (flavor.getMimeType().startsWith("text/plain"))
                return flavor;
        }

        return null;
    }


    protected boolean importCellObject(JTable table, int row, int column, Transferable transferable)
    {
        Class clazz = table.getColumnClass(column);
        DataFlavor[] flavors = transferable.getTransferDataFlavors();

        for (DataFlavor flavor : flavors)
        {
            if (flavor.getRepresentationClass().equals(clazz))
            {
                try
                {
                    System.out.println("importing " + flavor.getHumanPresentableName());
                    setCellValue(table, row, column, transferable.getTransferData(flavor));
                    return true;
                }
                catch (UnsupportedFlavorException e)
                {
                }
                catch (IOException e)
                {
                }
            }
        }

        return false;
    }


    protected void setCellValue(JTable table, int row, int column, Object newValue)
    {
    	table.setValueAt(newValue, row, column);
    }
}
