package com.getwemap.example.map

import com.getwemap.sdk.core.location.simulation.SimulatorLocationSource
import com.getwemap.sdk.core.model.entities.Coordinate
import com.getwemap.sdk.core.model.entities.MapData

object GareDeLyonSimulatorsLocationSource {

    fun defaultPath(mapData: MapData) = SimulatorLocationSource(mapData).apply {
        setCoordinates(
            listOf(
                Coordinate(48.84487592, 2.37362684, -1f),
                Coordinate(48.84490872, 2.37367312, -1f),
                Coordinate(48.84495440, 2.37373007, -1f),
                Coordinate(48.84498299, 2.37381412, -1f),
                Coordinate(48.84493822, 2.37389002, -1f),
                Coordinate(48.84487581, 2.37400763, 0f),
                Coordinate(48.84497481, 2.37394034, 0f),
                Coordinate(48.84502561, 2.37387502, 0f),
                Coordinate(48.84503426, 2.37384988, 0f),
                Coordinate(48.84508903, 2.37390382, 0f),
                Coordinate(48.84505252, 2.37398088, 0f),
                Coordinate(48.84500789, 2.37405793, 0f),
                Coordinate(48.84494907, 2.37414270, 0f),
                Coordinate(48.84487848, 2.37410577, 0f),
                Coordinate(48.84482785, 2.37396760, 0f),
                Coordinate(48.84475105, 2.37394103, 0f),
                Coordinate(48.84470028, 2.37378367, 0f),
                Coordinate(48.84459847, 2.37375206, 0f),
                Coordinate(48.84455509, 2.37363626, 0f),
                Coordinate(48.84447514, 2.37361814, 0f),
                Coordinate(48.84436643, 2.37375879, 0f),
                Coordinate(48.84428454, 2.37390447, 0f)
            )
        )
    }

    // Should use itinerary
    //  from: Coordinate(48.84458308799957, 2.3731548097070134, 0f)
    //  to: Coordinate(48.84511200990592, 2.3738383127780676, 0f)
    fun lessThan3Meters(mapData: MapData) = SimulatorLocationSource(mapData).apply {
        setCoordinates(
            listOf(
                Coordinate(48.84458308799957, 2.373154809707013, 0f),
                Coordinate(48.84464655892329, 2.373217708762632, 0f),
                Coordinate(48.84473578602785, 2.373312756224456, 0f),
                Coordinate(48.84482225337955, 2.373416190227028, 0f),
                Coordinate(48.84491515962298, 2.373523817499975, 0f),
                Coordinate(48.84499518763508, 2.373656604395169, 0f),
                Coordinate(48.84511752906156, 2.373790789047156, 0f)
            )
        )
    }

    // Should use itinerary
    //  from: Coordinate(48.84458308799957, 2.3731548097070134, 0f)
    //  to: Coordinate(48.84511200990592, 2.3738383127780676, 0f)
    fun lessThan3MetersAndRouteRecalculation(mapData: MapData) = SimulatorLocationSource(mapData).apply {
        setCoordinates(
            listOf(
                Coordinate(48.84455641179023, 2.373191151383594, 0f),
                Coordinate(48.84451869712519, 2.373259641466378, 0f),
                Coordinate(48.84447270359281, 2.373342109117077, 0f),
                Coordinate(48.84444418758151, 2.373445543119650, 0f),
                Coordinate(48.84449216826416, 2.373526551234251, 0f),
                Coordinate(48.84454353361512, 2.373613273934633, 0f),
                Coordinate(48.84461160393177, 2.373698537098915, 0f),
                Coordinate(48.84471094963327, 2.373817346426194, 0f),
                Coordinate(48.84479833689295, 2.373959917618930, 0f),
                Coordinate(48.84488112468253, 2.373989270511551, 0f),
                Coordinate(48.84494091577876, 2.374082920216584, 0f),
                Coordinate(48.84498046984935, 2.374035396485672, 0f),
                Coordinate(48.84505681831586, 2.373947337807806, 0f),
                Coordinate(48.84509729215452, 2.373860676886731, 0f)
            )
        )
    }

    // Should use itinerary
    //  from: Coordinate(48.84445563, 2.37319782, -1f)
    //  to: Coordinate(48.84502948, 2.37451864, 0f)
    fun fromLevelMinus1To0AndRouteRecalculation(mapData: MapData) = SimulatorLocationSource(mapData).apply {
        setCoordinates(
            listOf(
                Coordinate(48.84448534647855, 2.373249728368882, -1f),
                Coordinate(48.84452644860317, 2.373221720069951, -1f),
                Coordinate(48.84457030495670, 2.373236850990063, -1f),
                Coordinate(48.84461077141308, 2.373269366371580, -1f),
                Coordinate(48.84464509372218, 2.373313793328504, -1f),
                Coordinate(48.84462411898052, 2.373352747399431, -1f),
                Coordinate(48.84460462729353, 2.373394276946121, -1f),
                Coordinate(48.84458068661861, 2.373437415843228, -1f),
                Coordinate(48.84455356772417, 2.373487637620621, -1f),
                Coordinate(48.84452221148417, 2.373552668383655, 0f),
                Coordinate(48.84454848292983, 2.373610616588339, 0f),
                Coordinate(48.84459805965265, 2.373660194496791, 0f),
                Coordinate(48.84464339900575, 2.373749048410639, 0f),
                Coordinate(48.84471543340759, 2.373813435304733, 0f),
                Coordinate(48.84478323039703, 2.373890699577643, 0f),
                Coordinate(48.84484424760904, 2.373966676112673, 0f),
                Coordinate(48.84489382403906, 2.374057461633345, 0f),
                Coordinate(48.84494806144474, 2.374095449900860, 0f),
                Coordinate(48.84498153606426, 2.374034282351472, 0f),
                Coordinate(48.84505780726564, 2.374102532459210, 0f),
                Coordinate(48.84511246822183, 2.374203619882936, 0f),
                Coordinate(48.84518111212883, 2.374261568087620, 0f),
                Coordinate(48.84510992585310, 2.374347846525705, 0f),
                Coordinate(48.84507306149197, 2.374445714604726, 0f),
                Coordinate(48.84502433269711, 2.374511389236701, 0f)
            )
        )
    }

    // Should use itinerary
    //  from: Coordinate(48.84445563, 2.37319782, -1f)
    //  to: Coordinate(48.84502213, 2.37393831, -1f)
    fun fullPathLevelMinus1AndUserGoesLevel0(mapData: MapData) = SimulatorLocationSource(mapData).apply {
        setCoordinates(
            listOf(
                Coordinate(48.84448534647855, 2.373249728368882, -1f),
                Coordinate(48.84452644860317, 2.373221720069951, -1f),
                Coordinate(48.84457030495670, 2.373236850990063, -1f),
                Coordinate(48.84461077141308, 2.373269366371580, -1f),
                Coordinate(48.84464509372218, 2.373313793328504, -1f),
                Coordinate(48.84462411898052, 2.373352747399431, -1f),
                Coordinate(48.84460462729353, 2.373394276946121, -1f),
                Coordinate(48.84458068661861, 2.373437415843228, -1f),
                Coordinate(48.84455356772417, 2.373487637620621, -1f),
                Coordinate(48.84452221148417, 2.373552668383655, 0f),
                Coordinate(48.84454848292983, 2.373610616588339, 0f),
                Coordinate(48.84459805965265, 2.373660194496791, 0f),
                Coordinate(48.84464339900575, 2.373749048410639, 0f),
                Coordinate(48.84471543340759, 2.373813435304733, 0f),
                Coordinate(48.84478323039703, 2.373890699577643, 0f),
                Coordinate(48.84484424760904, 2.373966676112673, 0f),
                Coordinate(48.84488407867273, 2.373952510352104, 0f),
                Coordinate(48.84491967198376, 2.373890698933775, 0f),
                Coordinate(48.84495611272837, 2.373812146922982, -1f),
                Coordinate(48.84498746869670, 2.373781241213817, -1f),
                Coordinate(48.84501818905203, 2.373796372133928, -1f),
                Coordinate(48.84503005282449, 2.373813113692196, -1f),
                Coordinate(48.84501691722969, 2.373837258777481, -1f)
            )
        )
    }

    // Should use itinerary
    //  from: Coordinate(48.84482873, 2.37378956, 0f)
    //  to: Coordinate(48.8455159, 2.37305333)
    fun fromIndoorToOutdoor(mapData: MapData) = SimulatorLocationSource(mapData).apply {
        setCoordinates(
            listOf(
                Coordinate(48.84482873000000, 2.373789560000000, 0f),
                Coordinate(48.84486062569375, 2.373757659047842, 0f),
                Coordinate(48.84488710311881, 2.373677192777396, 0f),
                Coordinate(48.84493123212947, 2.373626230806112, 0f),
                Coordinate(48.84496035725518, 2.373636959642172, 0f),
                Coordinate(48.84499426877007, 2.373679471507966, 0f),
                Coordinate(48.84503096355028, 2.373725472539663, 0f),
                Coordinate(48.84505744088528, 2.373724131435156, 0f),
                Coordinate(48.84509274397683, 2.373653052896261, 0f),
                Coordinate(48.84514128568709, 2.373575268834829, 0f),
                Coordinate(48.84517394099296, 2.373493461459876),
                Coordinate(48.84522248262451, 2.373407630771399),
                Coordinate(48.84527014163515, 2.373434452861548),
                Coordinate(48.84532221346531, 2.373497484773398),
                Coordinate(48.84536457694821, 2.373532353490591),
                Coordinate(48.84539723210848, 2.373458592742682),
                Coordinate(48.84542106153632, 2.373403607457876),
                Coordinate(48.84544489095283, 2.373356668800116),
                Coordinate(48.84546254236514, 2.373305706828833),
                Coordinate(48.84548195891150, 2.373249380439520),
                Coordinate(48.84548460662180, 2.373190371841192),
                Coordinate(48.84548107634138, 2.373079060167075),
                Coordinate(48.84550225802012, 2.373058943599463)
            )
        )
    }
}