package com.momna.platform.ai;

/**
 * The registered output schemas. A kind without a built-in check (MY_DAY_CARDS_V1) is valid only through
 * a registered {@link AiPorts.OutputValidator}; with none registered it fails closed.
 */
public enum AiOutputSchemaKind { NON_EMPTY_TEXT, SAFETY_CLASSIFICATION_V1, MY_DAY_CARDS_V1 }
