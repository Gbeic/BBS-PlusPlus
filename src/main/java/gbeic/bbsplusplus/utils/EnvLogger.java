package gbeic.bbsplusplus.utils;

import net.neoforged.fml.loading.FMLEnvironment;
import org.slf4j.Logger;

public class EnvLogger {
    // NeoForge 没有 isDevelopmentEnvironment，用 production 取反判断
    private static final boolean IS_DEV = !FMLEnvironment.production;

    public static void info(Logger logger, String zhMsg, String enMsg, Object... args) {
        if (IS_DEV) {
            logger.info(zhMsg, args);
        } else {
            logger.info(enMsg, args);
        }
    }

    public static void warn(Logger logger, String zhMsg, String enMsg, Object... args) {
        if (IS_DEV) {
            logger.warn(zhMsg, args);
        } else {
            logger.warn(enMsg, args);
        }
    }

    public static void error(Logger logger, String zhMsg, String enMsg, Object... args) {
        if (IS_DEV) {
            logger.error(zhMsg, args);
        } else {
            logger.error(enMsg, args);
        }
    }

    public static void debug(Logger logger, String zhMsg, String enMsg, Object... args) {
        if (IS_DEV) {
            logger.debug(zhMsg, args);
        } else {
            logger.debug(enMsg, args);
        }
    }
}
