package com.kaimoana.integrations;

import com.kaimoana.KaimoanaConfig;
import com.kaimoana.registry.CatchResult;
import java.util.Locale;
import javax.inject.Inject;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import net.runelite.client.party.PartyService;
import net.runelite.client.party.messages.PartyMemberMessage;

public class PartyBroadcast
{
	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	@EqualsAndHashCode(callSuper = true)
	public static class KaimoanaCatch extends PartyMemberMessage
	{
		private String fish;
		private double kg;
		private String grade;
		private boolean shiny;
	}

	private final PartyService party;
	private final KaimoanaConfig config;

	@Inject
	public PartyBroadcast(PartyService party, KaimoanaConfig config)
	{
		this.party = party;
		this.config = config;
	}

	public void maybeSend(CatchResult r)
	{
		if (!config.party() || !party.isInParty() || !(r.isTrophyPlus() || r.isShiny()))
		{
			return;
		}
		party.send(new KaimoanaCatch(r.getEvent().getItemName(), r.getWeightKg(), r.getGrade().label(), r.isShiny()));
	}

	public static String describe(KaimoanaCatch c, String member)
	{
		return String.format(Locale.ROOT, "%s caught %s%s: %.2f kg (%s)",
			member, c.isShiny() ? "a shiny " : "", c.getFish(), c.getKg(), c.getGrade());
	}
}
