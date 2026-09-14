package android.system.virtualmachine;

/**
 * COMPILE-ONLY STUB. Never packaged into the APK — see probe2a/build.sh.
 *
 * Signatures read verbatim off the device's own framework-virtualization.jar
 * with `dexdump`, not recalled. Argument ORDER matters and is not guessable:
 * Partition's constructor is (name, imagePath, writable, guid) — confirmed
 * from the debug locals table in the disassembly, because the two adjacent
 * String parameters would silently swap without complaint.
 *
 * Every member declared here is marked BLOCKED in the device's hidden-API
 * metadata. They are declared anyway: the whole point of rung 2c is to make
 * the call and read what the platform throws, and javac needs a signature to
 * compile against before the runtime can refuse it.
 */
public class VirtualMachineCustomImageConfig {

    public String getName() {
        throw new UnsupportedOperationException("stub");
    }

    public String getKernelPath() {
        throw new UnsupportedOperationException("stub");
    }

    public Disk[] getDisks() {
        throw new UnsupportedOperationException("stub");
    }

    /** One entry in a disk's partition table. Mirrors a "partitions" element
     *  of /apex/com.android.virt/etc/microdroid.json. */
    public static final class Partition {

        public Partition(String name, String imagePath, boolean writable, String guid) {
            throw new UnsupportedOperationException("stub");
        }
    }

    /** A virtual disk. Either a whole image, or — as microdroid.json does it —
     *  no image and a list of partitions. RODisk(null) plus addPartition()
     *  reproduces the latter; the real factory stores the argument straight
     *  into the imagePath field, so null is the documented empty case. */
    public static final class Disk {

        public static Disk RODisk(String imagePath) {
            throw new UnsupportedOperationException("stub");
        }

        public static Disk RWDisk(String imagePath) {
            throw new UnsupportedOperationException("stub");
        }

        public Disk addPartition(Partition partition) {
            throw new UnsupportedOperationException("stub");
        }
    }

    public static class Builder {

        public Builder() {
            throw new UnsupportedOperationException("stub");
        }

        public Builder setName(String name) {
            throw new UnsupportedOperationException("stub");
        }

        public Builder setKernelPath(String path) {
            throw new UnsupportedOperationException("stub");
        }

        public Builder setInitrdPath(String path) {
            throw new UnsupportedOperationException("stub");
        }

        public Builder addDisk(Disk disk) {
            throw new UnsupportedOperationException("stub");
        }

        public Builder addParam(String param) {
            throw new UnsupportedOperationException("stub");
        }

        public Builder useNetwork(boolean network) {
            throw new UnsupportedOperationException("stub");
        }

        public Builder useAutoMemoryBalloon(boolean balloon) {
            throw new UnsupportedOperationException("stub");
        }

        public VirtualMachineCustomImageConfig build() {
            throw new UnsupportedOperationException("stub");
        }
    }
}
