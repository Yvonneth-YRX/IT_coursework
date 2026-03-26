
package views.html

import _root_.play.twirl.api.TwirlFeatureImports._
import _root_.play.twirl.api.TwirlHelperImports._
import _root_.play.twirl.api.Html
import _root_.play.twirl.api.JavaScript
import _root_.play.twirl.api.Txt
import _root_.play.twirl.api.Xml
import models._
import controllers._
import play.api.i18n._
import views.html._
import play.api.templates.PlayMagic._
import java.lang._
import java.util._
import scala.collection.JavaConverters._
import play.core.j.PlayMagicForJava._
import play.mvc._
import play.api.data.Field
import play.data._
import play.core.j.PlayFormsMagicForJava._

object gamescreen extends _root_.play.twirl.api.BaseScalaTemplate[play.twirl.api.HtmlFormat.Appendable,_root_.play.twirl.api.Format[play.twirl.api.HtmlFormat.Appendable]](play.twirl.api.HtmlFormat) with _root_.play.twirl.api.Template2[play.mvc.Http.Request,String,play.twirl.api.HtmlFormat.Appendable] {

  /**/
  def apply/*1.2*/(request: play.mvc.Http.Request, user: String):play.twirl.api.HtmlFormat.Appendable = {
    _display_ {
      {


Seq[Any](format.raw/*2.1*/("""<!DOCTYPE html>
<html>
    <head>
        <title>ITSD Card Game Main Screen</title>
        <meta charset="utf-8">
        <meta name="viewport" content="width=device-width, initial-scale=1">
        <link rel="stylesheet" href=""""),_display_(/*8.39*/routes/*8.45*/.Assets.at("css/uikit.css")),format.raw/*8.72*/("""" />
        
        <script src=""""),_display_(/*10.23*/routes/*10.29*/.Assets.at("js/jquery.3.4.1.js")),format.raw/*10.61*/(""""></script> 
        <script src=""""),_display_(/*11.23*/routes/*11.29*/.Assets.at("js/uikit.js")),format.raw/*11.54*/(""""></script>
        <script src=""""),_display_(/*12.23*/routes/*12.29*/.Assets.at("js/uikit-icons.js")),format.raw/*12.60*/(""""></script>
		<script src=""""),_display_(/*13.17*/routes/*13.23*/.Assets.at("js/hexi.min.js")),format.raw/*13.51*/(""""></script>

    </head>
    <body id="mainBody" wsdata=""""),_display_(/*16.34*/routes/*16.40*/.GameScreenController.socket.webSocketURL(request)),format.raw/*16.90*/("""" onload="init()">


	

	<script src=""""),_display_(/*21.16*/routes/*21.22*/.Assets.at("js/cardgame.js")),format.raw/*21.50*/(""""></script>
	<script type="text/javascript">
	
	// // Load them google fonts before starting...!
	window.WebFontConfig = """),format.raw/*25.25*/("""{"""),format.raw/*25.26*/("""
    	"""),format.raw/*26.6*/("""google: """),format.raw/*26.14*/("""{"""),format.raw/*26.15*/("""
        	"""),format.raw/*27.10*/("""families: ['Roboto']
    	"""),format.raw/*28.6*/("""}"""),format.raw/*28.7*/(""",

   		active: function() """),format.raw/*30.25*/("""{"""),format.raw/*30.26*/("""
        	"""),format.raw/*31.10*/("""// do something
        	init();
   		"""),format.raw/*33.6*/("""}"""),format.raw/*33.7*/("""
	"""),format.raw/*34.2*/("""}"""),format.raw/*34.3*/(""";
	
	let stageWidth = 1920;
	let stageHeight = 1080;
	let moveVelocity = 2;
	
	var ws;
	var userDataSession;
	var g;
	var gameActorInitalized = false;
	var gameStart = false;
	var sinceLastHeartbeat = 0;
	
	// game objects
	let boardTiles = new Map()
	let spriteContainers = new Map()
	let sprites = new Map()
	let attackLabels = new Map()
	let healthLabels = new Map()
	let handContainers = [null,null,null,null,null,null]
	let handSprites = [null,null,null,null,null,null];
	let cardJSON = [null,null,null,null,null,null];
	let cardPreview = null;
	let prevewCountdown = 0;
	
	let activeMoves = new Map()
	let activeProjectiles = [];
	let drawUnitQueue = [];
	let drawTileQueue = [];
	
	let player1ManaIcons = new Map()
	let player2ManaIcons = new Map()
	
	let player1Health = null;
	let player2Health = null;
	
	let player1Notification = null;
	let player2Notification = null;
	let player1NotificationText = null;
	let player2NotificationText = null;
	
	let playingEffects = [];
	
	function init() """),format.raw/*77.18*/("""{"""),format.raw/*77.19*/("""
		"""),format.raw/*78.3*/("""openWebSocketConnection();
	"""),format.raw/*79.2*/("""}"""),format.raw/*79.3*/("""
	
	
	"""),format.raw/*82.2*/("""function openWebSocketConnection() """),format.raw/*82.37*/("""{"""),format.raw/*82.38*/("""
        """),format.raw/*83.9*/("""var wsURL = document.getElementById("mainBody").getAttribute("wsdata");

        //alert(wsURL);
        ws = new WebSocket(wsURL);
        ws.onmessage = function (event) """),format.raw/*87.41*/("""{"""),format.raw/*87.42*/("""
            """),format.raw/*88.13*/("""var message;
            message = JSON.parse(event.data);
			console.log(message);
            switch (message.messagetype) """),format.raw/*91.42*/("""{"""),format.raw/*91.43*/("""
                case "actorReady":
					initHexi(message.preloadImages);

					gameActorInitalized = true;
					break;
				case "drawTile":
					//console.log(message);
					drawTileQueue.push(message);
					break;
				case "drawUnit":
					drawUnitQueue.push(message);
					break;
				case "moveUnit":
				    moveUnit(message.unitID,message.tilex,message.tiley);
                    break;
				case "moveUnitToTile":
					moveUnitToTile(message);
					break;
				case "setUnitHealth":
					setUnitHealth(message);
					break;
				case "setUnitAttack":
					setUnitAttack(message);
					break;
				case "setPlayer1Health":
					setPlayer1Health(message);
					break;
				case "setPlayer2Health":
					setPlayer2Health(message);
					break;
				case "setPlayer1Mana":
					setPlayer1Mana(message);
					break;
				case "setPlayer2Mana":
					setPlayer2Mana(message);
					break;
				case "addPlayer1Notification":
					addPlayer1Notification(message);
					break;
				case "addPlayer2Notification":
					addPlayer2Notification(message);
					break;
				case "playUnitAnimation":
					playUnitAnimation(message);
					break;
				case "drawCard":
					drawCard(message);
					break;
				case "deleteCard":
					deleteCard(message);
					break;
				case "playEffectAnimation":
					playEffectAnimation(message);
					break;
				case "deleteUnit":
					deleteUnit(message);
					break;
				case "drawProjectile":
					drawProjectile(message);
					break;
                default:
                    return console.log(message);
            """),format.raw/*154.13*/("""}"""),format.raw/*154.14*/("""
        """),format.raw/*155.9*/("""}"""),format.raw/*155.10*/(""";
	"""),format.raw/*156.2*/("""}"""),format.raw/*156.3*/("""
	
	"""),format.raw/*158.2*/("""</script>
     
    </body>
</html>
"""))
      }
    }
  }

  def render(request:play.mvc.Http.Request,user:String): play.twirl.api.HtmlFormat.Appendable = apply(request,user)

  def f:((play.mvc.Http.Request,String) => play.twirl.api.HtmlFormat.Appendable) = (request,user) => apply(request,user)

  def ref: this.type = this

}


              /*
                  -- GENERATED --
                  DATE: 2026-03-25T21:14:57.165
                  SOURCE: D:/javaproject/IT_coursework-master/ITSD-DT2025-26-Template/app/views/gamescreen.scala.html
                  HASH: 7c68f4cf56a62937cbe8e1ea3332641be4b2651a
                  MATRIX: 935->1|1075->48|1331->278|1345->284|1392->311|1455->347|1470->353|1523->385|1585->420|1600->426|1646->451|1707->485|1722->491|1774->522|1829->550|1844->556|1893->584|1978->642|1993->648|2064->698|2130->737|2145->743|2194->771|2343->892|2372->893|2405->899|2441->907|2470->908|2508->918|2561->944|2589->945|2644->972|2673->973|2711->983|2776->1021|2804->1022|2833->1024|2861->1025|3890->2026|3919->2027|3949->2030|4004->2058|4032->2059|4065->2065|4128->2100|4157->2101|4193->2110|4393->2282|4422->2283|4463->2296|4616->2421|4645->2422|6212->3960|6242->3961|6279->3970|6309->3971|6340->3974|6369->3975|6401->3979
                  LINES: 27->1|32->2|38->8|38->8|38->8|40->10|40->10|40->10|41->11|41->11|41->11|42->12|42->12|42->12|43->13|43->13|43->13|46->16|46->16|46->16|51->21|51->21|51->21|55->25|55->25|56->26|56->26|56->26|57->27|58->28|58->28|60->30|60->30|61->31|63->33|63->33|64->34|64->34|107->77|107->77|108->78|109->79|109->79|112->82|112->82|112->82|113->83|117->87|117->87|118->88|121->91|121->91|184->154|184->154|185->155|185->155|186->156|186->156|188->158
                  -- GENERATED --
              */
          