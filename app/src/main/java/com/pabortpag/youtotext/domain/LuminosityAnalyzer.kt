package com.pabortpag.youtotext.domain

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy

typealias GridListener = (grid: ByteArray, colors: IntArray?, width: Int, height: Int) -> Unit

class LuminosityAnalyzer(
    private val blockFactor: Int = 4,
    private val mirrorHorizontally: Boolean = false,
    private val extractColors: Boolean = false,
    private val gridListener: GridListener
) : ImageAnalysis.Analyzer {

    override fun analyze(image: ImageProxy) {
        try {
            val yPlane = image.planes[0]
            val yBuffer = yPlane.buffer
            val yRowStride = yPlane.rowStride
            val yPixelStride = yPlane.pixelStride
            val srcWidth = image.width
            val srcHeight = image.height
            val rotation = image.imageInfo.rotationDegrees

            val gridWidth = (srcWidth + blockFactor - 1) / blockFactor
            val gridHeight = (srcHeight + blockFactor - 1) / blockFactor
            val grid = ByteArray(gridWidth * gridHeight)
            val colors = if (extractColors) IntArray(gridWidth * gridHeight) else null

            if (extractColors) {
                val uPlane = image.planes[1]
                val vPlane = image.planes[2]
                fillGridWithColors(
                    yBuffer, yRowStride, yPixelStride,
                    uPlane.buffer, uPlane.rowStride, uPlane.pixelStride,
                    vPlane.buffer, vPlane.rowStride, vPlane.pixelStride,
                    srcWidth, srcHeight, gridWidth, gridHeight, grid, colors!!
                )
            } else {
                fillGridLuminanceOnly(yBuffer, yRowStride, srcWidth, srcHeight, gridWidth, gridHeight, grid)
            }

            val (alignedGrid, alignedColors, finalWidth, finalHeight) = applyGeometricCorrections(
                grid, colors, gridWidth, gridHeight, rotation, mirrorHorizontally
            )

            gridListener(alignedGrid, alignedColors, finalWidth, finalHeight)
        } finally {
            image.close()
        }
    }

    // Rellena el grid solo con luminancia
    private fun fillGridLuminanceOnly(
        yBuffer: java.nio.ByteBuffer, yRowStride: Int,
        srcWidth: Int, srcHeight: Int,
        gridWidth: Int, gridHeight: Int, grid: ByteArray
    ) {
        var cellIndex = 0
        for (blockY in 0 until gridHeight) {
            for (blockX in 0 until gridWidth) {
                val centerX = minOf(blockX * blockFactor + blockFactor / 2, srcWidth - 1)
                val centerY = minOf(blockY * blockFactor + blockFactor / 2, srcHeight - 1)
                val luminance = yBuffer.get(centerY * yRowStride + centerX).toInt() and 0xFF
                grid[cellIndex++] = luminance.toByte()
            }
        }
    }

    // Rellena el grid con luminancia y colores (conversión YUV→RGB)
    private fun fillGridWithColors(
        yBuffer: java.nio.ByteBuffer, yRowStride: Int, yPixelStride: Int,
        uBuffer: java.nio.ByteBuffer, uRowStride: Int, uPixelStride: Int,
        vBuffer: java.nio.ByteBuffer, vRowStride: Int, vPixelStride: Int,
        srcWidth: Int, srcHeight: Int,
        gridWidth: Int, gridHeight: Int,
        grid: ByteArray, colors: IntArray
    ) {
        var cellIndex = 0
        for (blockY in 0 until gridHeight) {
            for (blockX in 0 until gridWidth) {
                val centerX = minOf(blockX * blockFactor + blockFactor / 2, srcWidth - 1)
                val centerY = minOf(blockY * blockFactor + blockFactor / 2, srcHeight - 1)

                val y = yBuffer.get(centerY * yRowStride + centerX).toInt() and 0xFF
                val chromaX = centerX / 2
                val chromaY = centerY / 2
                val u = uBuffer.get(chromaY * uRowStride + chromaX * uPixelStride).toInt() and 0xFF
                val v = vBuffer.get(chromaY * vRowStride + chromaX * vPixelStride).toInt() and 0xFF

                val r = yuvToRed(y, u, v)
                val g = yuvToGreen(y, u, v)
                val b = yuvToBlue(y, u, v)

                colors[cellIndex] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                grid[cellIndex++] = y.toByte()
            }
        }
    }

    // Conversión YUV→RGB con aritmética entera (BT.601)
    private fun yuvToRed(y: Int, u: Int, v: Int): Int =
        ((298 * (y - 16) + 409 * (v - 128) + 128) shr 8).coerceIn(0, 255)

    private fun yuvToGreen(y: Int, u: Int, v: Int): Int =
        ((298 * (y - 16) - 100 * (u - 128) - 208 * (v - 128) + 128) shr 8).coerceIn(0, 255)

    private fun yuvToBlue(y: Int, u: Int, v: Int): Int =
        ((298 * (y - 16) + 516 * (u - 128) + 128) shr 8).coerceIn(0, 255)

    // Aplica las 3 correcciones geométricas: rotación, volteo horizontal y vertical
    private fun applyGeometricCorrections(
        grid: ByteArray, colors: IntArray?,
        width: Int, height: Int,
        rotation: Int, mirrorHorizontally: Boolean
    ): AlignedGrid {
        var currentGrid = grid
        var currentColors = colors
        var currentWidth = width
        var currentHeight = height

        // 1. Transposición (rotación del sensor)
        if (rotation == 90 || rotation == 270) {
            val rotatedGrid = ByteArray(currentGrid.size)
            val rotatedColors = if (currentColors != null) IntArray(currentColors.size) else null
            for (y in 0 until currentHeight) {
                for (x in 0 until currentWidth) {
                    rotatedGrid[x * currentHeight + y] = currentGrid[y * currentWidth + x]
                    if (rotatedColors != null) {
                        rotatedColors[x * currentHeight + y] = currentColors!![y * currentWidth + x]
                    }
                }
            }
            currentGrid = rotatedGrid
            currentColors = rotatedColors
            val temp = currentWidth
            currentWidth = currentHeight
            currentHeight = temp
        }

        // 2. Volteo horizontal (corrección base para todas las cámaras)
        val flippedHGrid = ByteArray(currentGrid.size)
        val flippedHColors = if (currentColors != null) IntArray(currentColors.size) else null
        for (y in 0 until currentHeight) {
            for (x in 0 until currentWidth) {
                flippedHGrid[y * currentWidth + (currentWidth - 1 - x)] = currentGrid[y * currentWidth + x]
                if (flippedHColors != null) {
                    flippedHColors[y * currentWidth + (currentWidth - 1 - x)] = currentColors!![y * currentWidth + x]
                }
            }
        }
        currentGrid = flippedHGrid
        currentColors = flippedHColors

        // 3. Volteo vertical (solo cámara frontal)
        if (mirrorHorizontally) {
            val flippedVGrid = ByteArray(currentGrid.size)
            val flippedVColors = if (currentColors != null) IntArray(currentColors.size) else null
            for (y in 0 until currentHeight) {
                for (x in 0 until currentWidth) {
                    flippedVGrid[(currentHeight - 1 - y) * currentWidth + x] = currentGrid[y * currentWidth + x]
                    if (flippedVColors != null) {
                        flippedVColors[(currentHeight - 1 - y) * currentWidth + x] = currentColors!![y * currentWidth + x]
                    }
                }
            }
            currentGrid = flippedVGrid
            currentColors = flippedVColors
        }

        return AlignedGrid(currentGrid, currentColors, currentWidth, currentHeight)
    }

    private data class AlignedGrid(
        val grid: ByteArray,
        val colors: IntArray?,
        val width: Int,
        val height: Int
    )
}