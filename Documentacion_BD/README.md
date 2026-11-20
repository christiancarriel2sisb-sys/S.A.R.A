# Documentación de Base de Datos — S.A.R.A

**Proyecto:** S.A.R.A — Sistema Asistente Robótico de Apoyo para Adultos Mayores  
**Institución:** Duoc UC — Sede Plaza Oeste (Ingeniería en Informática, Capstone)  
**Integrantes:** Francisca Otárola · Christian Carriel · Kevin Castro  
**Motor de Persistencia:** SQLite embebido administrado mediante **Android Room v4** (`sara_local_db.db`)  
**Fecha de actualización:** Noviembre 2026 (Fase 2)

---

## Contenido de esta Carpeta

| Documento | Descripción | Gráficos |
| :--- | :--- | :--- |
| [modelo_logico.md](./modelo_logico.md) | Modelo Lógico (Entidades, atributos semánticos, identificadores, cardinalidades, reglas de negocio y resolución N:M). | [PNG](./diagramas/modelo_logico.png) · [SVG](./diagramas/modelo_logico.svg) |
| [modelo_relacional.md](./modelo_relacional.md) | Modelo Relacional (Notación relacional, claves PK/FK/UK, índices, integridad referencial, análisis de normalización y DDL SQLite exacto de Room v4). | [PNG](./diagramas/modelo_relacional.png) · [SVG](./diagramas/modelo_relacional.svg) |
| [diccionario_de_datos.md](./diccionario_de_datos.md) | Diccionario de Datos exhaustivo con todas las tablas, columnas, tipos (SQLite / Kotlin / Lógico), formatos, restricciones, valores por defecto y ejemplos reales. | — |
| [poblacion_tablas_sara.sql](./poblacion_tablas_sara.sql) | Script SQL ejecutable (SQLite / Room v4) con creación de tablas, índices y carga completa de los 50 pacientes, 50 familiares, 10 recordatorios, 5 juegos y 125 sesiones de estimulación. | — |
| [Modelos_Base_de_Datos_SARA.docx](./Modelos_Base_de_Datos_SARA.docx) | Documento Word consolidado (listo para entrega Capstone) con portada, Modelo Lógico, Modelo Relacional, tablas de claves/índices/normalización, DDL SQLite y ambos gráficos PNG incrustados en alta resolución. | PNG incrustados |
| [Diccionario_de_Datos_SARA.docx](./Diccionario_de_Datos_SARA.docx) | Documento Word en formato horizontal (Landscape) con portada, convenciones, resumen general, fichas detalladas de las 8 tablas (SQLite / Kotlin / Lógico) y catálogo de los 5 juegos cognitivos. | — |

---

## Resumen del Esquema Actual (`SaraAppDatabase`, Versión 4)

| N° | Tabla física | Rol en el sistema | Registros poblados |
| :-: | :--- | :--- | :---: |
| 1 | `Paciente` | Entidad central de adultos mayores registrados (RUT como PK, borrado lógico). | 50 |
| 2 | `Contacto_emergencia` | Familiares / cuidadores vinculados al paciente (borrado lógico). | 50 |
| 3 | `recordatorios` | Alertas clínicas y cotidianas sincronizadas con `AlarmManager` y buffer RAM. | 10 |
| 4 | `juegos` | Catálogo de juegos de estimulación cognitiva administrados por SARA. | 5 |
| 5 | `historial_estimulacion` | Registro de sesiones jugadas, puntajes y métricas cognitivas por paciente. | 125 |
| 6 | `chat` | Mensajería privada local entre el paciente (voz) y sus familiares (texto). | 0 *(dinámica)* |
| 7 | `biometria_paciente` | Embedding facial de 192 dimensiones (MobileFaceNet) y consentimiento informado (relación 1:1). | 0 *(dinámica)* |

> **Archivo físico de referencia:** `Fase 2/Interfaz Usuario SARA/android/sara_local_db.db` (`PRAGMA user_version = 4`, hash Room `c156ccccc4ec2172821d7fb19d0ba657`).  
> **Poblador automático Android:** `Fase 2/Interfaz Usuario SARA/android/app/src/main/java/com/sara/robot/data/local/database/DatabaseSeeder.kt`.
