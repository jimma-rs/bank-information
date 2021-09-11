/*
 * Copyright (c) 2018, Psikoi <https://github.com/Psikoi>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package jimmars.bankinformation;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.DynamicGridLayout;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.QuantityFormatter;

@Slf4j
class BankInformationPanel extends PluginPanel
{
	private static final Color ODD_ROW = new Color(44, 44, 44);

	private final JPanel listContainer = new JPanel();

	private BankInformationTableHeader countHeader;
	private BankInformationTableHeader valueHeader;
	private BankInformationTableHeader nameHeader;

	private SortOrder orderIndex = SortOrder.VALUE;
	private boolean ascendingOrder = false;

	private ArrayList<BankInformationTableRow> rows = new ArrayList<>();
	private BankInformationPlugin plugin;

	private String filterString = "";

	private List<CachedItem> cachedItems = new ArrayList<>();
	private List<String> bankTags = new ArrayList<>();

	private JLabel bankValueAmountLabel;
	private JLabel filteredValueAmountLabel;
	private final JPanel totalValuePanel;
	private final JPanel filteredValuePanel;
	final JComboBox<String> bankTagsComboBox = new JComboBox<String>();

	BankInformationPanel(BankInformationPlugin plugin)
	{
		this.plugin = plugin;

		setBorder(null);
		setLayout(new DynamicGridLayout(0, 1));

		JPanel headerContainer = buildHeader();

		listContainer.setLayout(new GridLayout(0, 1));

		totalValuePanel = buildTotalValueBox();
		updateBankTotal();
		filteredValuePanel = buildFilteredValueBox();
		updateFilterTotal();

		add(totalValuePanel);
		add(filteredValuePanel);
		add(buildFilterByNameBox());
		add(buildFilterByTagBox());
		add(headerContainer);
		add(listContainer);
	}

	void updateList()
	{
		rows.sort((r1, r2) ->
		{
			switch (orderIndex)
			{
				case NAME:
					return r1.getItemName().compareTo(r2.getItemName()) * (ascendingOrder ? 1 : -1);
				case COUNT:
					return Integer.compare(r1.getItemCount(), r2.getItemCount()) * (ascendingOrder ? 1 : -1);
				case VALUE:
					return Integer.compare(r1.getPrice(), r2.getPrice()) * (ascendingOrder ? 1 : -1);
				default:
					return 0;
			}
		});

		listContainer.removeAll();

		for (int i = 0; i < rows.size(); i++)
		{
			BankInformationTableRow row = rows.get(i);
			row.setBackground(i % 2 == 0 ? ODD_ROW : ColorScheme.DARK_GRAY_COLOR);
			listContainer.add(row);
		}

		listContainer.revalidate();
		listContainer.repaint();
	}

	List<CachedItem> getFilteredValues()
	{
		List<CachedItem> filteredValues = new ArrayList<>();

		for (CachedItem item : cachedItems)
		{
			String selectedTag = String.valueOf(bankTagsComboBox.getSelectedItem());
			boolean tagFilter = selectedTag.equals("") || item.getTags().contains(String.valueOf(bankTagsComboBox.getSelectedItem()));
			if (tagFilter && item.getName().toLowerCase().contains(filterString.toLowerCase()))
			{
				filteredValues.add(item);
			}
		}
		return filteredValues;
	}

	void populate()
	{
		rows.clear();

		List<CachedItem> cachedItems = getFilteredValues();

		for (int i = 0; i < cachedItems.size(); i++)
		{
			rows.add(buildRow(cachedItems.get(i), i % 2 == 0));
		}

		updateList();
	}

	void setItems(List<CachedItem> items)
	{
		this.cachedItems = items;
		updateBankTotal();
		updateFilterTotal();
	}

	public void setTags(List<String> bankTags)
	{
		this.bankTags = bankTags;

		String currentSelectedItem = String.valueOf(bankTagsComboBox.getSelectedItem());
		bankTagsComboBox.removeAllItems();
		bankTagsComboBox.addItem("");
		bankTags.forEach(bankTagsComboBox::addItem);
		bankTagsComboBox.getModel().setSelectedItem(!currentSelectedItem.equals("null") ? currentSelectedItem : "");
	}

	private void orderBy(SortOrder order)
	{
		nameHeader.highlight(false, ascendingOrder);
		countHeader.highlight(false, ascendingOrder);
		valueHeader.highlight(false, ascendingOrder);

		switch (order)
		{
			case NAME:
				nameHeader.highlight(true, ascendingOrder);
				break;
			case COUNT:
				countHeader.highlight(true, ascendingOrder);
				break;
			case VALUE:
				valueHeader.highlight(true, ascendingOrder);
				break;
		}

		orderIndex = order;
		updateList();
	}

	/**
	 * Builds the entire table header.
	 */
	private JPanel buildHeader()
	{
		JPanel header = new JPanel(new BorderLayout());
		JPanel leftSide = new JPanel(new BorderLayout());
		JPanel rightSide = new JPanel(new BorderLayout());

		nameHeader = new BankInformationTableHeader("Name", orderIndex == SortOrder.NAME, ascendingOrder);
		nameHeader.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent mouseEvent)
			{
				if (SwingUtilities.isRightMouseButton(mouseEvent))
				{
					return;
				}
				ascendingOrder = orderIndex != SortOrder.NAME || !ascendingOrder;
				orderBy(SortOrder.NAME);
			}
		});

		countHeader = new BankInformationTableHeader("#", orderIndex == SortOrder.COUNT, ascendingOrder);
		countHeader.setPreferredSize(new Dimension(BankInformationTableRow.ITEM_COUNT_COLUMN_WIDTH, 0));
		countHeader.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent mouseEvent)
			{
				if (SwingUtilities.isRightMouseButton(mouseEvent))
				{
					return;
				}
				ascendingOrder = orderIndex != SortOrder.COUNT || !ascendingOrder;
				orderBy(SortOrder.COUNT);
			}
		});

		valueHeader = new BankInformationTableHeader("$", orderIndex == SortOrder.VALUE, ascendingOrder);
		valueHeader.setPreferredSize(new Dimension(BankInformationTableRow.ITEM_VALUE_COLUMN_WIDTH, 0));
		valueHeader.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent mouseEvent)
			{
				if (SwingUtilities.isRightMouseButton(mouseEvent))
				{
					return;
				}
				ascendingOrder = orderIndex != SortOrder.VALUE || !ascendingOrder;
				orderBy(SortOrder.VALUE);
			}
		});


		leftSide.add(nameHeader, BorderLayout.CENTER);
		leftSide.add(countHeader, BorderLayout.EAST);
		rightSide.add(valueHeader, BorderLayout.CENTER);

		header.add(leftSide, BorderLayout.CENTER);
		header.add(rightSide, BorderLayout.EAST);

		return header;
	}

	private JPanel buildTotalValueBox()
	{
		BorderLayout layout = new BorderLayout(1, 1);
		JPanel totalValuePanel = new JPanel(layout);
		totalValuePanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
		JLabel bankValueLabel = new JLabel("Total Value: ");
		bankValueAmountLabel = new JLabel("Not loaded");

		bankValueAmountLabel.setFont(FontManager.getRunescapeBoldFont());
		totalValuePanel.add(bankValueLabel, BorderLayout.LINE_START);
		totalValuePanel.add(bankValueAmountLabel, BorderLayout.CENTER);

		return totalValuePanel;
	}

	private JPanel buildFilteredValueBox()
	{
		BorderLayout layout = new BorderLayout(1, 1);
		JPanel totalValuePanel = new JPanel(layout);
		totalValuePanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
		JLabel bankValueLabel = new JLabel("Filtered Value: ");
		filteredValueAmountLabel = new JLabel("Not loaded");

		filteredValueAmountLabel.setFont(FontManager.getRunescapeBoldFont());
		totalValuePanel.add(bankValueLabel, BorderLayout.LINE_START);
		totalValuePanel.add(filteredValueAmountLabel, BorderLayout.CENTER);

		return totalValuePanel;
	}

	private void updateBankTotal()
	{
		int totalValue = cachedItems.stream().map(item -> item.getValue() * item.getQuantity()).mapToInt(Integer::intValue).sum();

		String totalValueString = totalValue > 0 ? QuantityFormatter.quantityToStackSize(totalValue) : "Not loaded";
		bankValueAmountLabel.setText(totalValueString);
		totalValuePanel.repaint();
	}

	private void updateFilterTotal()
	{
		int filteredValue = getFilteredValues().stream().map(item -> item.getValue() * item.getQuantity()).mapToInt(Integer::intValue).sum();
		String filteredValueString = filteredValue > 0 ? QuantityFormatter.quantityToStackSize(filteredValue) : "Not loaded";
		filteredValueAmountLabel.setText(filteredValueString);
		filteredValuePanel.repaint();
	}

	private JPanel buildFilterByNameBox()
	{
		BorderLayout layout = new BorderLayout(1, 1);
		JPanel filterPanel = new JPanel(layout);
		filterPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

		final JLabel filterByNameLabel = new JLabel("Filter by name: ");

		final JTextField filterByNameInput = new JTextField();

		filterByNameInput.addKeyListener(new java.awt.event.KeyListener()
		{
			@Override
			public void keyTyped(KeyEvent e)
			{
				filterString = filterByNameInput.getText();
				populate();
				updateFilterTotal();
			}

			@Override
			public void keyPressed(KeyEvent e)
			{
			}

			@Override
			public void keyReleased(KeyEvent e)
			{
			}
		});

		filterPanel.add(filterByNameLabel, BorderLayout.LINE_START);
		filterPanel.add(filterByNameInput, BorderLayout.CENTER);

		return filterPanel;
	}

	private JPanel buildFilterByTagBox()
	{
		BorderLayout layout = new BorderLayout(1, 1);
		JPanel filterPanel = new JPanel(layout);
		filterPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

		final JLabel filterByTagLabel = new JLabel("Filter by tag: ");

		bankTags.forEach(bankTagsComboBox::addItem);
		bankTagsComboBox.addActionListener(e -> {
			populate();
			updateFilterTotal();
		});

		filterPanel.add(filterByTagLabel, BorderLayout.LINE_START);
		filterPanel.add(bankTagsComboBox, BorderLayout.CENTER);

		return filterPanel;
	}

	/**
	 * Builds a table row, that displays the bank's information.
	 */
	private BankInformationTableRow buildRow(CachedItem item, boolean stripe)
	{
		BankInformationTableRow row = new BankInformationTableRow(item);
		row.setBackground(stripe ? ODD_ROW : ColorScheme.DARK_GRAY_COLOR);
		return row;
	}


	/**
	 * Enumerates the multiple ordering options for the bank list.
	 */
	private enum SortOrder
	{
		COUNT,
		VALUE,
		NAME
	}
}
