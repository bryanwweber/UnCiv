package com.unciv.logic.map

import com.badlogic.gdx.math.Vector2
import com.unciv.logic.GameInfo
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.models.gamebasics.GameBasics
import com.unciv.ui.utils.HexMath

class TileMap {

    @Transient
    @JvmField var gameInfo: GameInfo? = null

    private var tiles = HashMap<String, TileInfo>()

    constructor()  // for json parsing, we need to have a default constructor

    val values: MutableCollection<TileInfo>
        get() = tiles.values


    constructor(distance: Int) {
        tiles = SeedRandomMapGenerator().generateMap(distance)
        setTransients()
    }

    operator fun contains(vector: Vector2): Boolean {
        return tiles.containsKey(vector.toString())
    }

    operator fun get(vector: Vector2): TileInfo {
        return tiles[vector.toString()]!!
    }

    fun getTilesInDistance(origin: Vector2, distance: Int): List<TileInfo> {
        return HexMath.GetVectorsInDistance(origin, distance).filter {contains(it)}.map { get(it) }
    }

    fun getTilesAtDistance(origin: Vector2, distance: Int): List<TileInfo> {
        return HexMath.GetVectorsAtDistance(origin, distance).filter {contains(it)}.map { get(it) }

    }

    fun getDistanceToTilesWithinTurn(origin: Vector2, currentUnitMovement: Float, machineryIsResearched: Boolean): HashMap<TileInfo, Float> {
        val distanceToTiles = HashMap<TileInfo, Float>()
        distanceToTiles[get(origin)] = 0f
        var tilesToCheck = listOf(get(origin))
        while (!tilesToCheck.isEmpty()) {
            val updatedTiles = ArrayList<TileInfo>()
            for (tileToCheck in tilesToCheck)
                for (maybeUpdatedTile in getTilesInDistance(tileToCheck.position, 1)) {
                    var distanceBetweenTiles = maybeUpdatedTile.lastTerrain.movementCost.toFloat() // no road
                    if (tileToCheck.roadStatus !== RoadStatus.None && maybeUpdatedTile.roadStatus !== RoadStatus.None) //Road
                        distanceBetweenTiles = if (machineryIsResearched) 1 / 3f else 1 / 2f

                    if (tileToCheck.roadStatus === RoadStatus.Railroad && maybeUpdatedTile.roadStatus === RoadStatus.Railroad) // Railroad
                        distanceBetweenTiles = 1 / 10f

                    var totalDistanceToTile = distanceToTiles[tileToCheck]!! + distanceBetweenTiles
                    if (!distanceToTiles.containsKey(maybeUpdatedTile) || distanceToTiles[maybeUpdatedTile]!! > totalDistanceToTile) {
                        if (totalDistanceToTile < currentUnitMovement)
                            updatedTiles += maybeUpdatedTile
                        else
                            totalDistanceToTile = currentUnitMovement
                        distanceToTiles[maybeUpdatedTile] = totalDistanceToTile
                    }

                }

            tilesToCheck = updatedTiles
        }
        return distanceToTiles
    }

    fun getShortestPath(origin: Vector2, destination: Vector2, currentMovement: Float, maxMovement: Int, isMachineryResearched: Boolean): List<TileInfo> {
        var tilesToCheck: List<TileInfo> = listOf(get(origin))
        val movementTreeParents = HashMap<TileInfo, TileInfo?>() // contains a map of "you can get from X to Y in that turn"
        movementTreeParents[get(origin)] = null

        var distance = 1
        while (true) {
            val newTilesToCheck = ArrayList<TileInfo>()
            val distanceToDestination = HashMap<TileInfo, Float>()
            val movementThisTurn = if (distance == 1) currentMovement else maxMovement.toFloat()
            for (tileToCheck in tilesToCheck) {
                val distanceToTilesThisTurn = getDistanceToTilesWithinTurn(tileToCheck.position, movementThisTurn, isMachineryResearched)
                for (reachableTile in distanceToTilesThisTurn.keys) {
                    if(reachableTile.position == destination)
                        distanceToDestination.put(tileToCheck, distanceToTilesThisTurn[reachableTile]!!)
                    else {
                        if (movementTreeParents.containsKey(reachableTile)) continue // We cannot be faster than anything existing...
                        if (reachableTile.position != destination && reachableTile.unit != null) continue // This is an intermediary tile that contains a unit - we can't go there!
                        movementTreeParents[reachableTile] = tileToCheck
                        newTilesToCheck.add(reachableTile)
                    }
                }
            }

            if (distanceToDestination.isNotEmpty()) {
                val path = ArrayList<TileInfo>() // Traverse the tree upwards to get the list of tiles leading to the destination,
                var currentTile = distanceToDestination.minBy { it.value }!!.key
                while (currentTile.position != origin) {
                    path.add(currentTile)
                    currentTile = movementTreeParents[currentTile]!!
                }
                return path.reversed() // and reverse in order to get the list in chronological order
            }

            tilesToCheck = newTilesToCheck
            distance++
        }
    }

    fun placeUnitNearTile(position: Vector2, unitName: String, civInfo: CivilizationInfo) {
        val unit = GameBasics.Units[unitName]!!.getMapUnit()
        unit.owner = civInfo.civName
        unit.civInfo = civInfo
        val tilesInDistance = getTilesInDistance(position, 2)
        tilesInDistance.first { it.unit == null }.unit = unit // And if there's none, then kill me.
    }

    fun getViewableTiles(position: Vector2, sightDistance: Int): MutableList<TileInfo> {
        var sightDistance = sightDistance
        val viewableTiles = getTilesInDistance(position, 1).toMutableList()
        if (get(position).baseTerrain == "Hill") sightDistance += 1
        for (i in 1..sightDistance) { // in each layer,
            getTilesAtDistance(position, i).filterTo(viewableTiles) // take only tiles which have a visible neighbor, which is lower than the tile
                { tile -> tile.neighbors.any{viewableTiles.contains(it) && (it.height==0 || it.height < tile.height)}  }
        }

        return viewableTiles
    }

    fun setTransients() {
        for (tileInfo in values) tileInfo.tileMap = this
    }

}