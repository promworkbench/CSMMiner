package org.processmining.csmminer.statelogcreator;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Neil Weber
 */
public class CopyTransferable implements Transferable {
	protected Object object;
    protected String string;
    protected ArrayList<DataFlavor> flavors = new ArrayList<DataFlavor>();
    protected DataFlavor objectFlavor;

    protected static List<DataFlavor> stringFlavors = new ArrayList<DataFlavor>(3);

    static
    {
        try
        {
            stringFlavors.add(new DataFlavor("text/plain;class=java.lang.String"));
            stringFlavors.add(new DataFlavor(DataFlavor.javaJVMLocalObjectMimeType+";class=java.lang.String"));
            stringFlavors.add(DataFlavor.stringFlavor);

        }
        catch (ClassNotFoundException e)
        {
            System.err.println("error initializing CopyTransferable: " + e);
        }
    }


    public CopyTransferable(Object object)
    {
        this(object, object.toString());
    }


    public CopyTransferable(Object object, String string)
    {
        this.object = object;
        this.string = string;

        objectFlavor = new DataFlavor(object.getClass(), object.getClass().getSimpleName());
        flavors.add(objectFlavor);
        flavors.addAll(stringFlavors);
    }


    public DataFlavor[] getTransferDataFlavors()
    {
        DataFlavor[] arrayFlavors = new DataFlavor[flavors.size()];
        return flavors.toArray(arrayFlavors);
    }


    public boolean isDataFlavorSupported(DataFlavor flavor)
    {
        return flavors.contains(flavor);
    }


    public Object getTransferData(DataFlavor flavor)
        throws UnsupportedFlavorException, IOException
    {
        if (objectFlavor.equals(flavor))
        {
            if (object.getClass().equals(flavor.getRepresentationClass()))
                return object;
        }
        else if (isStringFlavor(flavor))
        {
            return string;
        }

        throw new UnsupportedFlavorException(flavor);
    }


    protected boolean isStringFlavor(DataFlavor flavor)
    {
        return stringFlavors.contains(flavor);
    }
}
