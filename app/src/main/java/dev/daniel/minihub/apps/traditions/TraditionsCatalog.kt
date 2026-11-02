package dev.daniel.minihub.apps.traditions

import dev.daniel.minihub.apps.traditions.DateRule.Advent
import dev.daniel.minihub.apps.traditions.DateRule.Easter
import dev.daniel.minihub.apps.traditions.DateRule.Fixed
import dev.daniel.minihub.apps.traditions.DateRule.NthWeekday
import dev.daniel.minihub.apps.traditions.DateRule.SchoolEndEast
import dev.daniel.minihub.apps.traditions.DateRule.WeekdayOnOrBefore
import dev.daniel.minihub.apps.traditions.Kind.KINDERGARTEN
import dev.daniel.minihub.apps.traditions.Kind.REGIONAL
import dev.daniel.minihub.apps.traditions.Kind.SCHOOL
import dev.daniel.minihub.apps.traditions.Kind.TRADITION
import dev.daniel.minihub.apps.traditions.Tier.CORE
import dev.daniel.minihub.apps.traditions.Tier.NICE
import dev.daniel.minihub.apps.traditions.Tier.OPTIONAL
import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.SATURDAY
import java.time.DayOfWeek.SUNDAY

/**
 * Built-in entries, from the "Austrian Family Traditions Calendar" doc.
 * Ids are stored in date overrides: don't rename an id once it is in use.
 */
val CATALOG: List<Tradition> = listOf(
    // ── Autumn & Advent ─────────────────────────────────────────────
    Tradition(
        "martini", "Martini", TRADITION, CORE, Fixed(11, 11), "11 November",
        "Burgenland's Landesfeiertag for its patron St. Martin; schools are closed. Lantern processions, " +
            "Martinigansl, and Martiniloben (tasting the new wine) in the lake villages in the weeks before.",
        listOf(Prep(14, "Book a table for Martinigansl"), Prep(3, "Plan the day off — schools are closed")),
    ),
    Tradition(
        "advent1", "1st Advent", TRADITION, CORE, Advent(1), "4th Sunday before Christmas",
        "Adventkranz with four candles, one more lit each Sunday. Advent calendar runs 1–24 Dec. " +
            "Christkindlmarkt visits.",
        listOf(Prep(14, "Buy or fill Advent calendars (24 small items each)"), Prep(3, "Buy or bind the Adventkranz + candles")),
    ),
    Tradition("advent2", "2nd Advent", TRADITION, OPTIONAL, Advent(2), "3rd Sunday before Christmas", "Light the second candle."),
    Tradition("advent3", "3rd Advent", TRADITION, OPTIONAL, Advent(3), "2nd Sunday before Christmas", "Light the third candle (Gaudete, often the pink one)."),
    Tradition("advent4", "4th Advent", TRADITION, OPTIONAL, Advent(4), "Last Sunday before Christmas", "Light all four candles."),
    Tradition(
        "christkind-letter", "Christkind letter", TRADITION, CORE, Fixed(11, 25), "Late November",
        "Kids write or draw a wish list to the Christkind and leave it on the windowsill. Doing it early " +
            "leaves time to order gifts.",
        listOf(Prep(0, "Sit down with the kids and write the Christkind letters")),
    ),
    Tradition(
        "barbara", "Barbaratag", TRADITION, NICE, Fixed(12, 4), "4 December",
        "Cut branches of cherry or other fruit trees and put them in water. If they bloom by Christmas, luck " +
            "follows. They need about three weeks somewhere bright and not too warm; change the water every 2–3 days.",
        listOf(Prep(2, "Find a tree with fat buds for Barbarazweige"), Prep(0, "Cut the Barbarazweige today")),
    ),
    Tradition(
        "krampus", "Krampus", TRADITION, NICE, Fixed(12, 5), "5 December",
        "Krampusläufe in many villages. Can scare small kids — decide whether to go. In the evening kids put " +
            "cleaned boots out for Nikolo.",
        listOf(Prep(0, "Kids put their boots out tonight")),
    ),
    Tradition(
        "nikolo", "Nikolo", TRADITION, CORE, Fixed(12, 6), "6 December",
        "Nikolo fills the boots with mandarins, nuts, peanuts and chocolate, sometimes a small gift.",
        listOf(Prep(10, "Buy Nikolo treats and a small gift"), Prep(1, "Fill the boots tonight")),
    ),
    Tradition(
        "maria-empfaengnis", "Mariä Empfängnis", TRADITION, OPTIONAL, Fixed(12, 8), "8 December",
        "Public holiday. Many families use it for Christmas markets or to buy the tree.",
    ),
    Tradition(
        "kekse", "Kekse backen", TRADITION, NICE, NthWeekday(12, SATURDAY, 1), "Early December",
        "Baking Vanillekipferl, Linzer and Lebkuchen with the kids.",
        listOf(Prep(3, "Buy baking ingredients; block two weekend slots")),
    ),
    Tradition(
        "tree", "Christmas tree", TRADITION, CORE, WeekdayOnOrBefore(12, 19, SATURDAY), "Weekend before Christmas",
        "Buy the tree. Traditionally the Christkind brings and decorates it on the 24th.",
        listOf(Prep(3, "Check tree stand and lights")),
    ),

    // ── Christmas to Fasching ───────────────────────────────────────
    Tradition(
        "heiliger-abend", "Heiliger Abend", TRADITION, CORE, Fixed(12, 24), "24 December",
        "The Christkind brings the tree and presents in the evening. A bell rings for the Bescherung, kids " +
            "recite a poem or sing, then Stille Nacht. Simple dinner (fish or Würstel), Christmette at night. " +
            "Shops close at midday.",
        listOf(
            Prep(35, "Order Christmas gifts online"),
            Prep(7, "Wrap gifts; plan dinner and who rings the bell"),
            Prep(1, "Last shopping — shops close at noon tomorrow"),
        ),
    ),
    Tradition(
        "christtag", "Christtag", TRADITION, CORE, Fixed(12, 25), "25 December",
        "Festive meal, visiting grandparents and relatives. Shops are closed 25 and 26 Dec.",
        listOf(Prep(10, "Plan who visits whom on 25/26 Dec; groceries")),
    ),
    Tradition("stefanitag", "Stefanitag", TRADITION, OPTIONAL, Fixed(12, 26), "26 December", "Public holiday, family visits."),
    Tradition(
        "silvester", "Silvester", TRADITION, NICE, Fixed(12, 31), "31 December",
        "Wachsgießen (lead is banned), lucky charms (Glücksschwein, Rauchfangkehrer, Kleeblatt), Donauwalzer " +
            "and the Pummerin at midnight. Fireworks have legal age categories.",
        listOf(Prep(7, "Buy Glücksbringer and a Wachsgießen set")),
    ),
    Tradition("neujahr", "Neujahr", TRADITION, OPTIONAL, Fixed(1, 1), "1 January", "Neujahrskonzert on TV, lucky charms handed out."),
    Tradition(
        "dreikoenig", "Heilige Drei Könige", TRADITION, NICE, Fixed(1, 6), "6 January",
        "Sternsinger — kids dressed as the three kings — go door to door from about 27 Dec, sing, collect for " +
            "the Dreikönigsaktion and chalk C+M+B with the year on the door.",
        listOf(Prep(36, "If the kids want to be Sternsinger: sign up with the parish"), Prep(10, "Have a donation ready for the Sternsinger")),
    ),
    Tradition(
        "lichtmess", "Maria Lichtmess", TRADITION, OPTIONAL, Fixed(2, 2), "2 February",
        "Traditional end of the Christmas season: crib and decorations come down; candles are blessed.",
    ),
    Tradition(
        "fasching", "Faschingsdienstag", TRADITION, CORE, Easter(-47), "47 days before Easter",
        "Peak of Fasching (which starts 11.11.). Costume parties, Faschingsumzug, Krapfen.",
        listOf(Prep(21, "Pick costumes with the kids"), Prep(5, "Costumes ready? Buy Krapfen")),
    ),
    Tradition(
        "valentinstag", "Valentinstag", TRADITION, OPTIONAL, Fixed(2, 14), "14 February",
        "Flowers or a gift for your partner; kids may craft something.",
        listOf(Prep(5, "Order flowers")),
    ),

    // ── Lent & Easter ───────────────────────────────────────────────
    Tradition(
        "aschermittwoch", "Aschermittwoch", TRADITION, OPTIONAL, Easter(-46), "46 days before Easter",
        "Start of Lent. Heringsschmaus, ash cross at church.",
    ),
    Tradition(
        "osterstrauch", "Osterstrauch", TRADITION, NICE, Easter(-14), "2 weeks before Easter",
        "Branches (pussy willow, forsythia) hung with blown or wooden eggs.",
        listOf(Prep(2, "Get branches and egg decorations for the Osterstrauch")),
    ),
    Tradition(
        "palmsonntag", "Palmsonntag", TRADITION, NICE, Easter(-7), "Sunday before Easter",
        "Kids carry Palmbuschen (pussy willow, Buchs, ribbons) to church to be blessed. The blessed bunch is " +
            "kept at home for the year.",
        listOf(Prep(5, "Bind or buy Palmbuschen; check procession time")),
    ),
    Tradition(
        "gruendonnerstag", "Gründonnerstag", TRADITION, OPTIONAL, Easter(-3), "Thursday before Easter",
        "Green meal (spinach, herbs). The church bells \"fly to Rome\" and stay silent until Easter.",
    ),
    Tradition(
        "eierfaerben", "Eier färben", TRADITION, CORE, Easter(-2), "Friday before Easter",
        "Dye eggs with the kids: onion skins, store dye kits, leaf stencils.",
        listOf(Prep(3, "Buy eggs, dye and vinegar")),
    ),
    Tradition(
        "karfreitag", "Karfreitag", TRADITION, NICE, Easter(-2), "Friday before Easter",
        "Meatless day. Ratschen: kids go round the village with wooden rattles instead of bells " +
            "(UNESCO intangible heritage).",
        listOf(Prep(14, "If the kids want to ratschen: sign up with the parish, borrow a Ratsche")),
    ),
    Tradition(
        "karsamstag", "Karsamstag", TRADITION, OPTIONAL, Easter(-1), "Saturday before Easter",
        "Speisensegnung (Fleischweihe): a basket with ham, eggs, Kren, Osterpinze or bread and salt is " +
            "blessed. Osterfeuer at night.",
        listOf(Prep(3, "Prepare the Speisensegnung basket; buy or bake Osterpinze")),
    ),
    Tradition(
        "ostern", "Ostersonntag", TRADITION, CORE, Easter(0), "Easter Sunday",
        "The Osterhase hides nests early in the morning; nest hunt in the garden; Eierpecken; Osterjause " +
            "with the blessed food. As godparents you bring Osterpackerl for your 2 godchildren.",
        listOf(
            Prep(21, "Buy nest gifts for the kids and Osterpackerl for the 2 godchildren"),
            Prep(10, "Sow cress or Osterweizen in the nest bowls"),
            Prep(3, "Deliver Osterpackerl to the godchildren"),
            Prep(1, "Hide the nests tonight / early tomorrow"),
        ),
    ),
    Tradition("ostermontag", "Ostermontag", TRADITION, OPTIONAL, Easter(1), "Monday after Easter", "Public holiday. Emmausgang (family walk), visiting relatives."),

    // ── Spring & summer ─────────────────────────────────────────────
    Tradition(
        "maibaum", "Maibaum / Staatsfeiertag", TRADITION, NICE, Fixed(5, 1), "1 May",
        "The village Maibaum is decorated and raised by hand on 30 Apr / 1 May with a fest. Guarding and " +
            "\"stealing\" it is part of the fun.",
        listOf(Prep(5, "Check the local Maibaumfest date")),
    ),
    Tradition(
        "muttertag", "Muttertag", TRADITION, CORE, NthWeekday(5, SUNDAY, 2), "2nd Sunday in May",
        "Kids bring crafts and poems from kindergarten. Breakfast in bed, flowers, family outing.",
        listOf(Prep(14, "Muttertag gift; book a restaurant if eating out"), Prep(3, "Flowers; help the kids with a card")),
    ),
    Tradition(
        "himmelfahrt", "Christi Himmelfahrt", TRADITION, OPTIONAL, Easter(39), "39 days after Easter",
        "Public holiday, often a bridge day.",
        listOf(Prep(14, "Take Friday off for a long weekend?")),
    ),
    Tradition(
        "pfingsten", "Pfingsten", TRADITION, OPTIONAL, Easter(49), "7 weeks after Easter",
        "Holiday weekend with Pfingstmontag; schools are off Saturday to Tuesday.",
        listOf(Prep(21, "Plan a short trip over Pfingsten?")),
    ),
    Tradition(
        "fronleichnam", "Fronleichnam", TRADITION, OPTIONAL, Easter(60), "60 days after Easter",
        "Procession with altars and flowers; first-communion kids walk along.",
    ),
    Tradition(
        "vatertag", "Vatertag", TRADITION, CORE, NthWeekday(6, SUNDAY, 2), "2nd Sunday in June",
        "Crafts from kindergarten, small gift, family outing.",
        listOf(Prep(10, "Vatertag is coming")),
    ),
    Tradition(
        "sonnwend", "Sommersonnenwende", TRADITION, OPTIONAL, Fixed(6, 21), "Around 21 June",
        "Sonnwendfeuer on hills and in villages.",
        listOf(Prep(3, "Check where the local Sonnwendfeuer is")),
    ),

    // ── Autumn ──────────────────────────────────────────────────────
    Tradition(
        "erntedank", "Erntedank", TRADITION, OPTIONAL, NthWeekday(10, SUNDAY, 1), "A Sunday in Sep/Oct (parish)",
        "Harvest thanksgiving: crown of grain, procession; kids bring harvest baskets. The date is set by the " +
            "parish — change it here once known.",
        listOf(Prep(7, "Ask the parish or kindergarten for the Erntedank date")),
        editable = true,
    ),
    Tradition(
        "nationalfeiertag", "Nationalfeiertag", TRADITION, OPTIONAL, Fixed(10, 26), "26 October",
        "Public holiday. Bundesheer show at Heldenplatz; many museums are free or open.",
    ),
    Tradition(
        "halloween", "Halloween", TRADITION, NICE, Fixed(10, 31), "31 October",
        "Imported but now common: costumes, pumpkin carving, trick-or-treating.",
        listOf(Prep(10, "Costumes; buy a pumpkin"), Prep(2, "Sweets for trick-or-treaters")),
    ),
    Tradition(
        "allerheiligen", "Allerheiligen", TRADITION, CORE, Fixed(11, 1), "1 November",
        "Visit family graves with lights and wreaths; blessing at the cemetery in the afternoon. Godparents " +
            "give their godchildren an Allerheiligenstriezel (sometimes with a coin baked in), traditionally " +
            "until Firmung — you have 2 godchildren.",
        listOf(
            Prep(10, "Buy grave decoration and lights (shops sell out)"),
            Prep(3, "Bake or order Striezel for the 2 godchildren; arrange handover"),
        ),
    ),
    Tradition("allerseelen", "Allerseelen", TRADITION, OPTIONAL, Fixed(11, 2), "2 November", "Remembering the dead. Schools are closed in all states.",
        listOf(Prep(7, "Childcare — schools are closed"))),

    // ── School calendar (Burgenland) ────────────────────────────────
    Tradition(
        "semesterferien", "Semesterferien start", SCHOOL, CORE, NthWeekday(2, MONDAY, 1), "1st Monday in February (Burgenland)",
        "One week of school holidays.",
        listOf(Prep(28, "Semesterferien in 4 weeks — plan childcare or a trip")),
        editable = true,
    ),
    Tradition(
        "osterferien", "Osterferien start", SCHOOL, CORE, Easter(-8), "Saturday before Palm Sunday",
        "School Easter holidays until Easter Monday.",
        listOf(Prep(28, "Osterferien in 4 weeks — plan childcare or a trip")),
    ),
    Tradition(
        "schulschluss", "Schulschluss", SCHOOL, CORE, SchoolEndEast, "Friday before the summer holidays (East)",
        "Zeugnis day; summer holidays start the next day and last 9 weeks. Small thank-you gift for teachers " +
            "or kindergarten staff.",
        listOf(Prep(42, "Summer holidays in 6 weeks — plan holidays and childcare"), Prep(7, "Thank-you gift and card for kindergarten staff")),
        editable = true,
    ),
    Tradition(
        "schulbeginn", "Schulbeginn", SCHOOL, CORE, NthWeekday(9, MONDAY, 1), "1st Monday in September (East)",
        "First school day; marks the end of the summer holidays.",
        listOf(Prep(21, "Check school/kindergarten lists and supplies"), Prep(3, "Back to the routine on Monday")),
        editable = true,
    ),
    Tradition(
        "herbstferien", "Herbstferien start", SCHOOL, CORE, Fixed(10, 27), "27–31 October",
        "Autumn school break nationwide.",
        listOf(Prep(21, "Herbstferien in 3 weeks — childcare or a trip")),
    ),

    // ── Kindergarten (best guesses, editable per year) ───────────────
    Tradition(
        "kg-laternenfest", "Laternenfest (kindergarten)", KINDERGARTEN, CORE, Fixed(11, 10), "Set per year",
        "Lantern procession for St. Martin. Use an LED stick, not a candle; dress warmly.",
        listOf(Prep(14, "Build or buy lanterns"), Prep(3, "Lanterns ready? LED batteries")),
        editable = true,
    ),
    Tradition(
        "kg-nikolo", "Nikolo visit (kindergarten)", KINDERGARTEN, CORE, Fixed(12, 7), "Set per year",
        "Nikolo visits the kindergarten. Some kindergartens ask parents to bring a Nikolosackerl.",
        listOf(Prep(10, "Nikolosackerl for kindergarten if requested"), Prep(1, "Nikolo at kindergarten tomorrow")),
        editable = true,
    ),
    Tradition(
        "kg-weihnachtsfeier", "Weihnachtsfeier (kindergarten)", KINDERGARTEN, NICE, WeekdayOnOrBefore(12, 23, FRIDAY), "Guess: last Friday before Christmas",
        "Christmas party at kindergarten.",
        listOf(Prep(7, "Cookies or contribution; small gift for the staff")),
        editable = true,
    ),
    Tradition(
        "kg-fasching", "Faschingsfest (kindergarten)", KINDERGARTEN, CORE, Easter(-47), "Guess: Faschingsdienstag",
        "Costume party at kindergarten.",
        listOf(Prep(21, "Costumes for both kids"), Prep(3, "Costumes ready?")),
        editable = true,
    ),
    Tradition(
        "kg-ostern", "Osterfest (kindergarten)", KINDERGARTEN, NICE, Easter(-9), "Guess: Friday before Palm Sunday",
        "Easter nest hunt at kindergarten.",
        listOf(Prep(7, "Small basket for the nest hunt if requested")),
        editable = true,
    ),
    Tradition(
        "kg-sommerfest", "Sommerfest (kindergarten)", KINDERGARTEN, NICE, NthWeekday(6, FRIDAY, -1), "Guess: last Friday in June",
        "Summer party with the families.",
        listOf(Prep(7, "Salad or cake for the buffet")),
        editable = true,
    ),

    // ── Regional holidays (optional) ────────────────────────────────
    Tradition("reg-josef", "Josefitag", REGIONAL, OPTIONAL, Fixed(3, 19), "19 March", "Patron day in Kärnten, Steiermark, Tirol and Vorarlberg."),
    Tradition("reg-florian", "Florianitag", REGIONAL, OPTIONAL, Fixed(5, 4), "4 May", "Patron day in Oberösterreich; St. Florian is patron of the fire brigades — local FF events."),
    Tradition("reg-rupert", "Rupertitag", REGIONAL, OPTIONAL, Fixed(9, 24), "24 September", "Patron day in Salzburg."),
    Tradition("reg-volksabstimmung", "Tag der Volksabstimmung", REGIONAL, OPTIONAL, Fixed(10, 10), "10 October", "State holiday in Kärnten."),
    Tradition("reg-leopold", "Leopolditag", REGIONAL, OPTIONAL, Fixed(11, 15), "15 November", "Patron day in Niederösterreich and Wien."),
)
