package com.hudhud.app.data.ai

import com.hudhud.app.domain.model.MessageDraft

/**
 * Analyzes batch size and provides recommendations.
 * Part of the Smart AI engine.
 */
class BatchSizeAnalyzer {

    // Thresholds
    private val smallBatchThreshold = 100
    private val mediumBatchThreshold = 500
    private val largeBatchThreshold = 1000
    private val veryLargeBatchThreshold = 2000

    /**
     * Analyze batch size and return analysis result
     */
    fun analyze(messages: List<MessageDraft>): BatchAnalysis {
        val total = messages.size
        val valid = messages.count { it.isValid }
        
        val category = categorizeBatch(total)
        val complexity = calculateComplexity(messages)
        val estimatedTime = estimateProcessingTime(total, complexity)
        
        return BatchAnalysis(
            totalMessages = total,
            validMessages = valid,
            invalidMessages = total - valid,
            category = category,
            complexity = complexity,
            estimatedTimeMinutes = estimatedTime,
            recommendations = generateRecommendations(total, category, complexity),
        )
    }

    /**
     * Categorize batch size
     */
    private fun categorizeBatch(size: Int): BatchCategory {
        return when {
            size <= smallBatchThreshold -> BatchCategory.SMALL
            size <= mediumBatchThreshold -> BatchCategory.MEDIUM
            size <= largeBatchThreshold -> BatchCategory.LARGE
            size <= veryLargeBatchThreshold -> BatchCategory.VERY_LARGE
            else -> BatchCategory.HUGE
        }
    }

    /**
     * Calculate batch complexity based on message characteristics
     */
    private fun calculateComplexity(messages: List<MessageDraft>): BatchComplexity {
        if (messages.isEmpty()) return BatchComplexity.SIMPLE
        
        var score = 0
        
        // Check for errors
        val errorRate = messages.count { it.errors.isNotEmpty() }.toFloat() / messages.size
        if (errorRate > 0.2f) score += 2
        else if (errorRate > 0.1f) score += 1
        
        // Check for duplicates
        val phones = messages.map { it.phone }
        val duplicateRate = 1f - (phones.distinct().size.toFloat() / phones.size)
        if (duplicateRate > 0.1f) score += 2
        else if (duplicateRate > 0.05f) score += 1
        
        // Check message length variation
        val lengths = messages.map { it.text.length }
        val avgLength = lengths.average()
        val variance = lengths.map { (it - avgLength) * (it - avgLength) }.average()
        val stdDev = kotlin.math.sqrt(variance)
        
        if (stdDev > avgLength) score += 2
        else if (stdDev > avgLength * 0.5) score += 1
        
        return when {
            score >= 5 -> BatchComplexity.VERY_COMPLEX
            score >= 3 -> BatchComplexity.COMPLEX
            score >= 1 -> BatchComplexity.MODERATE
            else -> BatchComplexity.SIMPLE
        }
    }

    /**
     * Estimate processing time in minutes
     */
    private fun estimateProcessingTime(total: Int, complexity: BatchComplexity): Int {
        val baseTimePerMessage = when (complexity) {
            BatchComplexity.SIMPLE -> 10 // ms
            BatchComplexity.MODERATE -> 20
            BatchComplexity.COMPLEX -> 35
            BatchComplexity.VERY_COMPLEX -> 50
        }
        
        val totalMs = total * baseTimePerMessage
        return (totalMs / 60000).toInt().coerceAtLeast(1)
    }

    /**
     * Generate recommendations based on analysis
     */
    private fun generateRecommendations(
        total: Int,
        category: BatchCategory,
        complexity: BatchComplexity,
    ): List<String> {
        
        val recommendations = mutableListOf<String>()
        
        // Size-based recommendations
        when (category) {
            BatchCategory.SMALL -> {
                recommendations.add("Batch is small - direct processing is recommended")
            }
            BatchCategory.MEDIUM -> {
                recommendations.add("Batch size is moderate - progress indicator recommended")
            }
            BatchCategory.LARGE -> {
                recommendations.add("Batch is large - consider using background processing")
                recommendations.add("Show progress to keep user informed")
            }
            BatchCategory.VERY_LARGE, BatchCategory.HUGE -> {
                recommendations.add("Batch is very large - use WorkManager for background processing")
                recommendations.add("Consider splitting into smaller batches")
                recommendations.add("Add cancellation option")
            }
        }
        
        // Complexity-based recommendations
        when (complexity) {
            BatchComplexity.COMPLEX, BatchComplexity.VERY_COMPLEX -> {
                recommendations.add("Complex batch - thorough validation recommended")
                if (category == BatchCategory.LARGE || category == BatchCategory.VERY_LARGE) {
                    recommendations.add("May take several minutes - consider scheduling")
                }
            }
            else -> {}
        }
        
        return recommendations
    }

    /**
     * Get suggested batch size for processing
     */
    fun getSuggestedBatchSize(total: Int): Int {
        return when {
            total <= 100 -> total
            total <= 500 -> 100
            total <= 1000 -> 200
            total <= 2000 -> 300
            else -> 500
        }
    }

    /**
     * Batch category enum
     */
    enum class BatchCategory {
        SMALL,      // <= 100
        MEDIUM,     // 101-500
        LARGE,      // 501-1000
        VERY_LARGE, // 1001-2000
        HUGE,       // > 2000
    }

    /**
     * Batch complexity enum
     */
    enum class BatchComplexity {
        SIMPLE,
        MODERATE,
        COMPLEX,
        VERY_COMPLEX,
    }

    /**
     * Batch analysis result
     */
    data class BatchAnalysis(
        val totalMessages: Int,
        val validMessages: Int,
        val invalidMessages: Int,
        val category: BatchCategory,
        val complexity: BatchComplexity,
        val estimatedTimeMinutes: Int,
        val recommendations: List<String>,
    ) {
        val successRate: Float
            get() = if (totalMessages > 0) validMessages.toFloat() / totalMessages else 0f
        
        val shouldUseBackground: Boolean
            get() = category == BatchCategory.LARGE || 
                    category == BatchCategory.VERY_LARGE || 
                    category == BatchCategory.HUGE
    }
}