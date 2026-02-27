package utils;

/**
 * This is a utility class that just has short-cuts to the location of various
 * config files. 
 * 
 * @author Dr. Richard McCreadie
 *
 */
public class StaticConfFiles {

	// Board Pieces
	public final static String tileConf = "conf/gameconfs/tile.json";
	public final static String gridConf = "conf/gameconfs/grid.json";
	
	// Avatars
	public final static String humanAvatar = "conf/gameconfs/avatars/avatar1.json";
	public final static String aiAvatar = "conf/gameconfs/avatars/avatar2.json";
	
	// Tokens
	public final static String wraithling = "conf/gameconfs/units/wraithling.json";
	
	// Effects
	public final static String f1_inmolation = "conf/gameconfs/effects/f1_inmolation.json";
	public final static String f1_buff = "conf/gameconfs/effects/f1_buff.json";
	public final static String f1_martyrdom = "conf/gameconfs/effects/f1_martyrdom.json";
	public final static String f1_projectiles = "conf/gameconfs/effects/f1_projectiles.json";
	public final static String f1_summon = "conf/gameconfs/effects/f1_summon.json";

    // Human cards
    public final static String[] humanCards = {
            "conf/gameconfs/cards/1_1_c_u_bad_omen.json",
            "conf/gameconfs/cards/1_2_c_s_hornoftheforsaken.json",
            "conf/gameconfs/cards/1_3_c_u_gloom_chaser.json",
            "conf/gameconfs/cards/1_4_c_u_shadow_watcher.json",
            "conf/gameconfs/cards/1_5_c_s_wraithling_swarm.json",
            "conf/gameconfs/cards/1_6_c_u_nightsorrow_assassin.json",
            "conf/gameconfs/cards/1_7_c_u_rock_pulveriser.json",
            "conf/gameconfs/cards/1_8_c_s_dark_terminus.json",
            "conf/gameconfs/cards/1_9_c_u_bloodmoon_priestess.json",
            "conf/gameconfs/cards/1_a1_c_u_shadowdancer.json"
    };

    // AI cards
    public final static String[] aiCards = {
            "conf/gameconfs/cards/2_1_c_u_skyrock_golem.json",
            "conf/gameconfs/cards/2_2_c_u_swamp_entangler.json",
            "conf/gameconfs/cards/2_3_c_u_silverguard_knight.json",
            "conf/gameconfs/cards/2_4_c_u_saberspine_tiger.json",
            "conf/gameconfs/cards/2_5_c_s_beamshock.json",
            "conf/gameconfs/cards/2_6_c_u_young_flamewing.json",
            "conf/gameconfs/cards/2_7_c_u_silverguard_squire.json",
            "conf/gameconfs/cards/2_8_c_u_ironcliff_guardian.json",
            "conf/gameconfs/cards/2_9_c_s_sundrop_elixir.json",
            "conf/gameconfs/cards/2_a1_c_s_truestrike.json"
    };
}
