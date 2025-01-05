package com.example.obd2cloud

import com.github.eltonvs.obd.command.ATCommand
import com.github.eltonvs.obd.command.Switcher

/**
 * Declaration of other OBD command.
 * In this class are specified commands that are not included in the library, specially init codes
 */


class HeadersCommand(header: Switcher) : ATCommand() {
    override val tag = "HEADER"
    override val name = "Set at header"
    override val mode = "AT"
    override val pid = "H0"
}

class SetMemoryCommand(value: Switcher) : ATCommand() {
    override val tag = "MEMORY"
    override val name = "Memory on/off"
    override val mode = "AT"
    override val pid = "M0"
}

class DeviceDescriptorCommand : ATCommand() {
    override val tag = "DESC"
    override val name = "Device descriptor"
    override val mode = "AT"
    override val pid = "@1"
}

class DisplayProtoNumberCommand : ATCommand() {
    override val tag = "DPROTO"
    override val name = "Dispaly protocol number"
    override val mode = "AT"
    override val pid = "DPN"
}

class DeviceInfoCommand : ATCommand() {
    override val tag = "INFO"
    override val name = "Device information"
    override val mode = "AT"
    override val pid = "I"
}
