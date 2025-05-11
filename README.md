# 🚗 Clasificación del Estilo de Conducción con Android, OBD-II, Sensores Móviles y Machine Learning

Este proyecto forma parte de mi Trabajo de Fin de Grado (TFG) en la Universidad Politécnica de Madrid. Consiste en el desarrollo de una aplicación Android capaz de analizar el estilo de conducción en tiempo real mediante la integración de datos del vehículo (OBD-II), sensores del dispositivo móvil (acelerómetro y giroscopio) y modelos de Machine Learning en la nube.

## 📱 Características principales

- 🔗 Conexión Bluetooth con dispositivos OBD-II.
- 📊 Recogida de datos clave: RPM, velocidad, carga del motor, posición del acelerador, etc.
- 📱 Uso de sensores del móvil para detectar maniobras bruscas.
- ☁️ Clasificación del estilo de conducción (tranquilo, normal, agresivo) usando modelos ML en Azure.
- 💡 Recomendaciones personalizadas basadas en los datos del trayecto.
- 📤 Exportación de datos en formato Excel y JSON.

## 🎯 Objetivo

Desarrollar una app Android accesible y educativa que permita al usuario mejorar su estilo de conducción, reduciendo el consumo de combustible y las emisiones contaminantes.

## 🧪 Tecnologías utilizadas

- **Android + Kotlin**: desarrollo de la app.
- **Bluetooth + API OBD-II (kotlin-obd-api)**: conexión con el coche.
- **Sensores del móvil**: acelerómetro y giroscopio.
- **Here Maps API**: obtención de la velocidad máxima permitida en la vía.
- **Azure Machine Learning**: entrenamiento y despliegue de modelos ML (Voting Ensemble, LightGBM).
- **Excel/JSON**: almacenamiento y análisis de datos.

## 🧠 Modelos de Machine Learning

Se entrenaron dos enfoques:
- **Modelo completo**: incluye todas las variables disponibles.
- **Modelo refinado**: utiliza solo las variables con mayor impacto estadístico.

Precisión alcanzada: **98.94%** en condiciones reales (modelo completo).

## 🌍 Impacto

- 🚘 Mejora de hábitos de conducción.
- 🌱 Contribución a la sostenibilidad medioambiental.
- 📚 Aplicación en educación vial, seguros basados en comportamiento y flotas comerciales.

## 📸 Capturas de pantalla

*(Añadir)*

[![Ask DeepWiki](https://deepwiki.com/badge.svg)](https://deepwiki.com/Yushetf33/OBD2Cloud)
