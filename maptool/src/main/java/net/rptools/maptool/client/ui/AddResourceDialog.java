/*
 * This software copyright by various authors including the RPTools.net
 * development team, and licensed under the LGPL Version 3 or, at your option,
 * any later version.
 *
 * Portions of this software were originally covered under the Apache Software
 * License, Version 1.1 or Version 2.0.
 *
 * See the file LICENSE elsewhere in this distribution for license details.
 */

package net.rptools.maptool.client.ui;

import com.jidesoft.swing.FolderChooser;
import net.rptools.lib.FileUtil;
import net.rptools.maptool.client.*;
import net.rptools.maptool.client.swing.AbeillePanel;
import net.rptools.maptool.client.swing.GenericDialog;
import net.rptools.maptool.language.I18N;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.jdesktop.swingworker.SwingWorker;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;

public class AddResourceDialog extends AbeillePanel<AddResourceDialog.Model> {

	private static final Logger log = Logger.getLogger(AddResourceDialog.class);

	private static final String LIBRARY_URL = "http://library.rptools.net/1.3";
	//private static final String LIBRARY_LIST_URL = LIBRARY_URL + "/listArtPacks";
	private static final String LIBRARY_LIST_URL = "http://test.lukasjacobs.de/lib.txt";

	public enum Tab {
		LOCAL, WEB, RPTOOLS
	}

	private GenericDialog dialog;
	private Model model;
	private boolean downloadLibraryListInitiated;

	private boolean install = false;

	public AddResourceDialog() {
		super("net/rptools/maptool/client/ui/forms/addResourcesDialog.xml");

		setPreferredSize(new Dimension(550, 300));

		panelInit();
	}

	public boolean getInstall() {
		return install;
	}

	public void showDialog() {
		dialog = new GenericDialog("Add Resource to Library", MapTool.getFrame(), this);

		model = new Model();

		bind(model);

		getRootPane().setDefaultButton(getInstallButton());
		dialog.showDialog();
	}

	@Override
	public Model getModel() {
		return model;
	}

	public JButton getInstallButton() {
		return (JButton) getComponent("installButton");
	}

	public JTextField getBrowseTextField() {
		return (JTextField) getComponent("@localDirectory");
	}

	public JTable getLibraryList() {
		return (JTable) getComponent("@rptoolsList");
	}

	public void initLibraryList() {
		JTable list = getLibraryList();
		list.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);

		list.setModel(new MessageTableModel(I18N.getText("dialog.addresource.downloading")));
	}

	public void initTabPane() {

		final JTabbedPane tabPane = (JTabbedPane) getComponent("tabPane");

		tabPane.getModel().addChangeListener(new ChangeListener() {
			public void stateChanged(ChangeEvent e) {
				// Hmmm, this is fragile (breaks if the order changes) rethink this later
				switch (tabPane.getSelectedIndex()) {
				case 0:
					model.tab = Tab.LOCAL;
					break;
				case 1:
					model.tab = Tab.WEB;
					break;
				case 2:
					model.tab = Tab.RPTOOLS;
					downloadLibraryList();
					break;
				}
			}
		});
	}

	public void initLocalDirectoryButton() {
		final JButton button = (JButton) getComponent("localDirectoryButton");
		button.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {

				FolderChooser folderChooser = new FolderChooser();
				folderChooser.setCurrentDirectory(MapTool.getFrame().getLoadFileChooser().getCurrentDirectory());
				folderChooser.setRecentListVisible(false);
				folderChooser.setFileHidingEnabled(true);
				folderChooser.setDialogTitle(I18N.getText("msg.title.loadAssetTree"));

				int result = folderChooser.showOpenDialog(button.getTopLevelAncestor());
				if (result == FolderChooser.APPROVE_OPTION) {
					File root = folderChooser.getSelectedFolder();
					getBrowseTextField().setText(root.getAbsolutePath());
				}
			}
		});
	}

	public void initInstallButton() {
		JButton button = (JButton) getComponent("installButton");
		button.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				install = true;
				if (commit()) {
					close();
				}
			}
		});
	}

	public void initCancelButton() {
		JButton button = (JButton) getComponent("cancelButton");
		button.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				close();
			}
		});
	}

	private String getSizeString(int size) {
		NumberFormat format = NumberFormat.getNumberInstance();
		if (size < 1000) {
			return format.format(size) + " bytes";
		}
		if (size < 1000000) {
			return format.format(size / 1000) + " k";
		}
		return format.format(size / 1000000) + " mb";
	}

	private void downloadLibraryList() {
		if (downloadLibraryListInitiated) {
			return;
		}

		// This pattern is safe because it is only called on the EDT
		downloadLibraryListInitiated = true;

		new SwingWorker<Object, Object>() {
			TableModel model;

			@Override
			protected Object doInBackground() throws Exception {
				String result = null;
				try {
					WebDownloader downloader = new WebDownloader(new URL(LIBRARY_LIST_URL));
					result = downloader.read();
				} finally {
					if (result == null) {
						model = new MessageTableModel(I18N.getText("dialog.addresource.errorDownloading"));
						return null;
					}
				}
				LibraryTableModel tableModel = new LibraryTableModel();

				// Create a list to compare against for dups
				List<String> libraryNameList = new ArrayList<String>();
				for (File file : AppPreferences.getAssetRoots()) {
					libraryNameList.add(file.getName());
				}
				// Generate the list
				try {
					BufferedReader reader = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(result.getBytes())));
					String line = null;
					while ((line = reader.readLine()) != null) {
						LibraryRow row = new LibraryRow(line);

						// Don't include if we've already got it
						if (libraryNameList.contains(row.name)) {
							continue;
						}
						tableModel.addElement(row);
					}
					model = tableModel;

					//Add a TableRowSorter
					TableRowSorter<LibraryTableModel> sorter = new TableRowSorter<>();
					getLibraryList().setRowSorter(sorter);
					sorter.setModel((LibraryTableModel) model);

					//Set the custom renderer for size
					getLibraryList().setDefaultRenderer(Integer.class, new SizeCellRenderer());

				} catch (Throwable t) {
					log.error("unable to parse library list", t);
					model = new MessageTableModel(I18N.getText("dialog.addresource.errorDownloading"));
				}

				return null;
			}

			@Override
			protected void done() {
				getLibraryList().setModel(model);
				getLibraryList().repaint();
			}
		}.execute();
	}

	@Override
	public boolean commit() {
		if (!super.commit()) {
			return false;
		}

		// Add the resource
		final List<LibraryRow> rowList = new ArrayList<LibraryRow>();

		switch (model.getTab()) {
		case LOCAL:
			if (StringUtils.isEmpty(model.getLocalDirectory())) {
				MapTool.showMessage("dialog.addresource.warn.filenotfound", "Error", JOptionPane.ERROR_MESSAGE, model.getLocalDirectory());
				return false;
			}
			File root = new File(model.getLocalDirectory());
			if (!root.exists()) {
				MapTool.showMessage("dialog.addresource.warn.filenotfound", "Error", JOptionPane.ERROR_MESSAGE, model.getLocalDirectory());
				return false;
			}
			if (!root.isDirectory()) {
				MapTool.showMessage("dialog.addresource.warn.directoryrequired", "Error", JOptionPane.ERROR_MESSAGE, model.getLocalDirectory());
				return false;
			}
			try {
				AppSetup.installLibrary(FileUtil.getNameWithoutExtension(root), root);
			} catch (MalformedURLException e) {
				log.error("Bad path url: " + root.getPath(), e);
				MapTool.showMessage("dialog.addresource.warn.badpath", "Error", JOptionPane.ERROR_MESSAGE, model.getLocalDirectory());
				return false;
			} catch (IOException e) {
				log.error("IOException adding local root: " + root.getPath(), e);
				MapTool.showMessage("dialog.addresource.warn.badpath", "Error", JOptionPane.ERROR_MESSAGE, model.getLocalDirectory());
				return false;
			}
			return true;

		case WEB:
			if (StringUtils.isEmpty(model.getUrlName())) {
				MapTool.showMessage("dialog.addresource.warn.musthavename", "Error", JOptionPane.ERROR_MESSAGE, model.getLocalDirectory());
				return false;
			}
			// validate the url format so that we don't hit it later
			try {
				new URL(model.getUrl());
			} catch (MalformedURLException e) {
				MapTool.showMessage("dialog.addresource.warn.invalidurl", "Error", JOptionPane.ERROR_MESSAGE, model.getUrl());
				return false;
			}
			rowList.add(new LibraryRow("unknown", model.getUrlName(), model.getUrl(), -1)); //TODO: Don't default to "Unknow" artist
			break;

		case RPTOOLS:
			if (getLibraryList().getSelectedRowCount() == 0) {
				MapTool.showMessage("dialog.addresource.warn.mustselectone", "Error", JOptionPane.ERROR_MESSAGE);
				return false;
			}

			ArrayList<LibraryRow> selectedRows = new ArrayList<>();
			LibraryTableModel model = (LibraryTableModel) getLibraryList().getModel();
			int[] selectedRowIndices = getLibraryList().getSelectedRows();
			for (int i = 0; i < getLibraryList().getSelectedRowCount(); i++) {
				int modelRowIndex = getLibraryList().convertRowIndexToModel(selectedRowIndices[i]);
				selectedRows.add(model.getRow(modelRowIndex));
			}

			for (Object obj : selectedRows) {
				LibraryRow row = (LibraryRow) obj;

				//validate the url format
				row.path = LIBRARY_URL + "/" + row.path;
				try {
					new URL(row.path);
				} catch (MalformedURLException e) {
					MapTool.showMessage("dialog.addresource.warn.invalidurl", "Error", JOptionPane.ERROR_MESSAGE, row.path);
					return false;
				}
				rowList.add(row);
			}

			break;
		}

		new SwingWorker<Object, Object>() {
			@Override
			protected Object doInBackground() throws Exception {
				for (LibraryRow row : rowList) {
					try {
						RemoteFileDownloader downloader = new RemoteFileDownloader(new URL(row.path));
						File tmpFile = downloader.read();
						AppSetup.installLibrary(row.name, tmpFile.toURL());
						tmpFile.delete();
					} catch (IOException e) {
						log.error("Error downloading library: " + e, e);
						MapTool.showInformation("dialog.addresource.warn.couldnotload");
					}
				}
				return null;
			}
		}.execute();
		return true;
	}

	private void close() {
		unbind();
		dialog.closeDialog();
	}

	private static class LibraryRow {
		private final String artist;
		private final String name;
		private String path;
		private final int size;

		public LibraryRow(String artist, String name, String path, int size) {
			this.artist = artist.trim();
			this.name = name.trim();
			this.path = path.trim();
			this.size = size;
		}

		public LibraryRow(String row) {
			String[] data = row.split("\\|");

			artist = data[0].trim();
			name = data[1].trim();
			path = data[2].trim();
			size = Integer.parseInt(data[3]);
		}
	}

	public static class Model {
		private String localDirectory;
		private String urlName;
		private String url;
		private Tab tab = Tab.LOCAL;

		public String getLocalDirectory() {
			return localDirectory;
		}

		public void setLocalDirectory(String localDirectory) {
			this.localDirectory = localDirectory;
		}

		public String getUrl() {
			return url;
		}

		public void setUrl(String url) {
			this.url = url;
		}

		public Tab getTab() {
			return tab;
		}

		public void setTab(Tab tab) {
			this.tab = tab;
		}

		public String getUrlName() {
			return urlName;
		}

		public void setUrlName(String urlName) {
			this.urlName = urlName;
		}
	}

	private class MessageListModel extends AbstractListModel {
		private final String message;

		public MessageListModel(String message) {
			this.message = message;
		}

		public Object getElementAt(int index) {
			return message;
		}

		public int getSize() {
			return 1;
		}
	}

	private class MessageTableModel extends AbstractTableModel {
		private final String message;

		public MessageTableModel(String message) {
			this.message = message;
		}

		@Override
		public int getRowCount() {
			return 1;
		}

		@Override
		public int getColumnCount() {
			return 1;
		}

		@Override
		public Object getValueAt(int rowIndex, int columnIndex) {
			return message;
		}
	}

	private class LibraryTableModel extends AbstractTableModel {

		private ArrayList<LibraryRow> rows;

		LibraryTableModel() {
			rows = new ArrayList<>();
		}

		@Override
		public String getColumnName(int column) {
			switch (column) {
				case 0: //Artist
					return I18N.getText("dialog.addresource.artist");

				case 1: //Name
					return I18N.getText("dialog.addresource.artpackname");

				case 2: //Size
					return I18N.getText("dialog.addresource.size");

				default:
					return null;
			}
		}

		@Override
		public int getRowCount() {
			return rows.size();
		}

		@Override
		public int getColumnCount() {
			return 3; //Author, Name, Size
		}

		@Override
		public Object getValueAt(int rowIndex, int columnIndex) {
			LibraryRow row = rows.get(rowIndex);

			switch (columnIndex) {
				case 0: //Author
					return row.artist;

				case 1: //Name
					return row.name;

				case 2: //Size
					return row.size;

				default:
					return null;
			}
		}

		@Override
		public Class<?> getColumnClass(int columnIndex) {
			switch (columnIndex) {
				case 0: //Author
					return String.class;

				case 1: //Name
					return String.class;

				case 2: //Size
					return Integer.class;

				default:
					return null;
			}
		}

		public void addElement(LibraryRow row) {
			if (rows.contains(row))
				return;

			rows.add(row);
			fireTableDataChanged();
		}

		public LibraryRow getRow(int rowIndex) {
			if(rowIndex >= rows.size() || rowIndex < 0)
				return null;

			return rows.get(rowIndex);
		}
	}

	private class SizeCellRenderer extends DefaultTableCellRenderer {
		@Override
		public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
			super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
			Integer size = (Integer) value;
			setText(getSizeString(size.intValue()));

			return this;
		}
	}
}
