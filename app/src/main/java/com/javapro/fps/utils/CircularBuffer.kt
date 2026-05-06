package com.javapro.fps.utils

import java.util.concurrent.atomic.AtomicInteger

class CircularBuffer(private val capacity: Int) {
    private val buffer = FloatArray(capacity)
    private var head = 0
    private var size = 0

    fun add(value: Float) {
        buffer[head] = value
        head = (head + 1) % capacity
        if (size < capacity) size++
    }

    fun toList(): List<Float> {
        val result = mutableListOf<Float>()
        val start = if (size < capacity) 0 else head
        for (i in 0 until size) {
            result.add(buffer[(start + i) % capacity])
        }
        return result
    }

    fun clear() {
        head = 0
        size = 0
    }

    fun getLatest(): Float = if (size > 0) buffer[(head - 1 + capacity) % capacity] else 0f

    fun getSize() = size

    fun getCapacity() = capacity
}

class GenericCircularBuffer<T>(private val capacity: Int) {
    private val buffer = arrayOfNulls<Any>(capacity) as Array<T?>
    private var head = 0
    private var size = 0

    fun add(value: T) {
        buffer[head] = value
        head = (head + 1) % capacity
        if (size < capacity) size++
    }

    fun toList(): List<T> {
        val result = mutableListOf<T>()
        val start = if (size < capacity) 0 else head
        for (i in 0 until size) {
            buffer[(start + i) % capacity]?.let { result.add(it) }
        }
        return result
    }

    fun clear() {
        head = 0
        size = 0
    }

    fun getSize() = size
}
