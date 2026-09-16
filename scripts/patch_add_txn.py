import re

with open('app/src/main/java/com/example/ui/screens/add/AddTransactionDialog.kt', 'r') as f:
    content = f.read()

# I will find the exact lines for the end of the `items` block and insert my code.
# Based on earlier sed output:
#                                         )
#                                     }
#                                 }
#                             }
#                         }
#                     }
#                 }
#                 Spacer(modifier = Modifier.height(16.dp))

target = """                                        )
                                    }
                                }
                            }
                        }
                    }
                }"""

replacement = """                                        )
                                    }
                                }
                            }
                            item {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    modifier = Modifier.clickable { onManageCategories() }
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(28.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Settings,
                                                contentDescription = "Управление",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Управление",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }"""

if target in content:
    content = content.replace(target, replacement)
    with open('app/src/main/java/com/example/ui/screens/add/AddTransactionDialog.kt', 'w') as f:
        f.write(content)
    print("Success")
else:
    print("Not found")
