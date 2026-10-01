package org.perfribbon.platform.windows

import com.sun.jna.Platform
import com.sun.jna.platform.win32.User32
import com.sun.jna.ptr.IntByReference

fun foregroundProcessId(): Int? {
    if (!Platform.isWindows()) return null
    val window = User32.INSTANCE.GetForegroundWindow() ?: return null
    val pid = IntByReference()
    User32.INSTANCE.GetWindowThreadProcessId(window, pid)
    return pid.value.takeIf { it > 0 && it.toLong() != ProcessHandle.current().pid() }
}
