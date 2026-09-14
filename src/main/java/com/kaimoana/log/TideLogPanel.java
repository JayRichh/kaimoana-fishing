package com.kaimoana.log;

import com.kaimoana.KaimoanaConfig;
import com.kaimoana.registry.FishEntry;
import com.kaimoana.registry.FishRegistry;
import com.kaimoana.registry.Grade;
import com.kaimoana.unlocks.UnlockEngine;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.HashMap;
import java.util.stream.Collectors;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.AsyncBufferedImage;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.LinkBrowser;

public class TideLogPanel extends PluginPanel
{
	enum Sort
	{
		RECENT("Recent"),
		HEAVIEST("Heaviest"),
		MOST("Most caught"),
		RAREST("Rarest"),
		AZ("A-Z");

		final String label;

		Sort(String label)
		{
			this.label = label;
		}

		@Override
		public String toString()
		{
			return label;
		}
	}

	private final ItemManager items;
	private final FishRegistry registry;
	private final KaimoanaConfig config;

	private final JLabel header = new JLabel();
	private final JTextField search = new JTextField();
	private final JComboBox<Sort> sort = new JComboBox<>(Sort.values());
	private final JPanel rows = new JPanel();

	private TideLog log = new TideLog();
	private Map<String, Integer> itemIds = new HashMap<>();

	public TideLogPanel(ItemManager items, FishRegistry registry, KaimoanaConfig config)
	{
		this.items = items;
		this.registry = registry;
		this.config = config;

		setLayout(new BorderLayout(0, 6));
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		JPanel top = new JPanel(new GridLayout(0, 1, 0, 4));
		top.setOpaque(false);
		JLabel title = new JLabel("Tide Log");
		title.setForeground(Color.WHITE);
		title.setFont(title.getFont().deriveFont(Font.BOLD, 16f));
		header.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		top.add(title);
		top.add(header);
		top.add(search);
		top.add(sort);
		add(top, BorderLayout.NORTH);

		rows.setLayout(new BoxLayout(rows, BoxLayout.Y_AXIS));
		rows.setOpaque(false);
		JScrollPane scroll = new JScrollPane(rows);
		scroll.setBorder(null);
		scroll.getVerticalScrollBar().setUnitIncrement(16);
		add(scroll, BorderLayout.CENTER);

		JButton export = new JButton("Copy CSV to clipboard");
		export.addActionListener(e -> Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(csv()), null));
		add(export, BorderLayout.SOUTH);

		search.getDocument().addDocumentListener(new DocumentListener()
		{
			public void insertUpdate(DocumentEvent e)
			{
				rebuild();
			}

			public void removeUpdate(DocumentEvent e)
			{
				rebuild();
			}

			public void changedUpdate(DocumentEvent e)
			{
				rebuild();
			}
		});
		sort.addActionListener(e -> rebuild());
	}

	public void refresh(TideLog log, Map<String, Integer> itemIds)
	{
		this.log = log;
		this.itemIds = itemIds;
		SwingUtilities.invokeLater(this::rebuild);
	}

	private void rebuild()
	{
		long caught = registry.all().stream().filter(e -> log.species.containsKey(e.getName())).count();
		String title = log.unlocks.stream().filter(UnlockEngine::isTitle).map(UnlockEngine::label)
			.reduce((a, b) -> b).orElse("Deckhand");
		header.setText(String.format(Locale.ROOT, "<html>%s<br>%d/%d species, %d catches, %d shiny</html>",
			title, caught, registry.size(), log.totalCatches, log.totalShinies));

		rows.removeAll();
		String q = search.getText().toLowerCase(Locale.ROOT);
		List<FishEntry> list = new ArrayList<>(registry.all());
		for (String name : log.species.keySet())
		{
			if (registry.find(name).isEmpty())
			{
				list.add(FishEntry.fallback(name, 0));
			}
		}
		if (!config.silhouettes())
		{
			list.removeIf(e -> !log.species.containsKey(e.getName()));
		}
		list.removeIf(e -> !e.getName().toLowerCase(Locale.ROOT).contains(q));

		Comparator<FishEntry> cmp;
		switch ((Sort) sort.getSelectedItem())
		{
			case HEAVIEST:
				cmp = Comparator.comparingDouble((FishEntry e) -> stat(e).heaviest).reversed();
				break;
			case MOST:
				cmp = Comparator.comparingLong((FishEntry e) -> stat(e).count).reversed();
				break;
			case RAREST:
				cmp = Comparator.comparingInt(FishEntry::getRarity).reversed();
				break;
			case AZ:
				cmp = Comparator.comparing(FishEntry::getName);
				break;
			default:
				cmp = Comparator.comparingLong((FishEntry e) -> Math.max(stat(e).heaviestAt, stat(e).firstAt)).reversed();
		}
		list.sort(cmp);
		for (FishEntry e : list)
		{
			rows.add(row(e));
		}
		rows.revalidate();
		rows.repaint();
	}

	private TideLog.Species stat(FishEntry e)
	{
		return log.species.getOrDefault(e.getName(), new TideLog.Species());
	}

	private JComponent row(FishEntry e)
	{
		TideLog.Species s = stat(e);
		boolean caught = s.count > 0;
		JPanel p = new JPanel(new BorderLayout(6, 0));
		p.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		p.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));

		JLabel icon = new JLabel();
		icon.setPreferredSize(new Dimension(36, 32));
		int id = itemIds.getOrDefault(e.getName(), -1);
		if (id >= 0)
		{
			AsyncBufferedImage img = items.getImage(id);
			Runnable apply = () -> icon.setIcon(new ImageIcon(caught ? img : ImageUtil.luminanceScale(ImageUtil.grayscaleImage(img), 0.4f)));
			img.onLoaded(apply);
			apply.run();
		}
		p.add(icon, BorderLayout.WEST);

		String best = caught ? String.format(Locale.ROOT, "%.2f kg", s.heaviest) : "-";
		String name = caught ? e.getName() : "???";
		if (s.shinies > 0)
		{
			name += " *";
		}
		Grade bestGrade = s.grades.keySet().stream().map(Grade::valueOf).max(Comparator.naturalOrder()).orElse(null);
		String gradeHtml = bestGrade == null ? "" : String.format(" <span style='color:#%06x'>%s</span>", bestGrade.color().getRGB() & 0xFFFFFF, bestGrade.label());
		JLabel text = new JLabel(String.format("<html><b>%s</b>%s<br><span style='color:#aaaaaa'>%d caught, best %s, %s</span></html>",
			name, gradeHtml, s.count, best, stars(e.getRarity())));
		text.setForeground(caught ? Color.WHITE : Color.GRAY);
		p.add(text, BorderLayout.CENTER);

		p.setToolTipText(caught ? detail(e, s) : "Not caught yet");
		p.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent ev)
			{
				if (caught && ev.getClickCount() == 2)
				{
					LinkBrowser.browse("https://oldschool.runescape.wiki/w/" + e.getName().replace(' ', '_'));
				}
			}
		});
		p.setMaximumSize(new Dimension(Integer.MAX_VALUE, 48));
		return p;
	}

	private static String stars(int n)
	{
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < n; i++)
		{
			sb.append('*');
		}
		return sb.toString();
	}

	private String detail(FishEntry e, TideLog.Species s)
	{
		String grades = Arrays.stream(Grade.values())
			.map(g -> g.label() + " " + s.grades.getOrDefault(g.name(), 0L))
			.collect(Collectors.joining(", "));
		String locs = log.locationBests.entrySet().stream()
			.filter(x -> x.getValue().containsKey(e.getName()))
			.map(x -> x.getKey() + " " + String.format(Locale.ROOT, "%.2f", x.getValue().get(e.getName())) + " kg")
			.collect(Collectors.joining("<br>"));
		return "<html>" + grades + "<br>" + (locs.isEmpty() ? "" : "<b>Location bests</b><br>" + locs + "<br>") + "<i>Double-click for wiki</i></html>";
	}

	private String csv()
	{
		StringBuilder sb = new StringBuilder("fish,count,heaviest_kg,shinies,first_caught_ms\n");
		for (Map.Entry<String, TideLog.Species> x : log.species.entrySet())
		{
			TideLog.Species v = x.getValue();
			sb.append(x.getKey()).append(',').append(v.count).append(',').append(v.heaviest).append(',').append(v.shinies).append(',').append(v.firstAt).append('\n');
		}
		return sb.toString();
	}
}
