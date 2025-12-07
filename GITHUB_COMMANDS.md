# Guide des commandes Git et GitHub

Ce guide rapide explique les commandes Git les plus courantes pour interagir avec GitHub, en particulier pour la sauvegarde de votre projet.

---

## 1. Initialiser un nouveau dépôt Git local (`git init`)

Si votre projet n'est pas déjà un dépôt Git, cette commande le transforme en un.

`git init`

## 2. Ajouter des fichiers à la zone de staging (`git add`)

Cette commande ajoute les modifications de votre répertoire de travail à la zone de staging de Git. C'est la première étape avant de "commettre" (sauvegarder) vos modifications.

-   **Ajouter tous les fichiers modifiés/nouveaux :**
    `git add .`
-   **Ajouter un fichier spécifique :**
    `git add <nom-du-fichier>`

## 3. Enregistrer les modifications dans l'historique (`git commit`)

Une fois que vous avez ajouté des fichiers à la zone de staging, vous les "committez" pour enregistrer ces modifications dans l'historique de votre dépôt local. Chaque commit doit avoir un message décrivant les changements.

`git commit -m "Votre message de commit ici"`

## 4. Connecter votre dépôt local à un dépôt GitHub distant (`git remote add`)

Cette commande établit une connexion entre votre dépôt local et un dépôt vide sur GitHub (ou tout autre service d'hébergement Git). `origin` est le nom par défaut donné au dépôt distant.

`git remote add origin <URL_du_dépôt_GitHub>`

_Exemple :_
`git remote add origin https://github.com/votre_nom_utilisateur/votre_repo.git`

## 5. Télécharger les modifications depuis GitHub (`git pull`)

Pour récupérer les dernières modifications d'un dépôt distant vers votre dépôt local. C'est crucial si vous travaillez en équipe ou si vous avez fait des modifications directement sur GitHub.

`git pull origin <nom_de_branche>`

_Exemple pour la branche `main` ou `master` :_
`git pull origin main`

## 6. Envoyer vos modifications vers GitHub (`git push`)

Cette commande télécharge vos commits locaux (vos sauvegardes) vers votre dépôt GitHub.

-   **Premier push (pour définir la branche amont) :**
    `git push -u origin <nom_de_branche>`
    Le flag `-u` (ou `--set-upstream`) associe votre branche locale à la branche distante spécifiée, de sorte que les `git push` et `git pull` ultérieurs dans cette branche n'auront pas besoin de spécifier `origin <nom_de_branche>`.

-   **Pushes ultérieurs :**
    `git push`

-   **Push forcé (à utiliser avec prudence !) :**
    `git push -f origin <nom_de_branche>`
    Le push forcé remplace l'historique de la branche distante par votre historique local. **À n'utiliser que si vous êtes absolument sûr de ce que vous faites**, car cela peut entraîner la perte du travail d'autres personnes. Dans le cas d'une correction de l'historique (comme avec Git LFS après un commit initial), c'est parfois nécessaire sur votre propre branche non partagée.

## 7. Vérifier l'état de votre dépôt (`git status`)

Affiche l'état des fichiers dans votre répertoire de travail et votre zone de staging. Il vous indique quels fichiers ont été modifiés, quels fichiers sont suivis, etc.

`git status`

## 8. Cloner un dépôt existant depuis GitHub (`git clone`)

Si vous souhaitez obtenir une copie locale d'un dépôt GitHub existant, utilisez `git clone`. Cela va créer un nouveau répertoire avec tous les fichiers du dépôt et son historique.

`git clone <URL_du_dépôt_GitHub>`

_Exemple :_
`git clone https://github.com/votre_nom_utilisateur/votre_repo.git`

---

## Git Large File Storage (LFS)

Git LFS est une extension de Git qui gère les fichiers volumineux en remplaçant les fichiers réels par des pointeurs de texte dans le dépôt Git, tout en stockant le contenu réel du fichier sur un serveur distant.

### Commandes LFS importantes :

1.  **Installer Git LFS (sur votre machine) :**
    `git lfs install`
    (Cela doit être fait une fois par machine.)

2.  **Demander à LFS de suivre un type de fichier ou un fichier spécifique :**
    `git lfs track "*.psd"` (pour suivre tous les fichiers `.psd`)
    `git lfs track "RSC/STM_GTFS_DATA/stop_times.txt"` (pour suivre un fichier spécifique)
    Cette commande ajoute une entrée au fichier `.gitattributes`. Vous devez committer ce fichier.

3.  **Pour migrer des fichiers volumineux existants vers LFS (réécrit l'historique) :**
    `git lfs migrate import --include="<fichier_ou_pattern>" --everything`
    **Attention :** Ceci réécrit l'historique de votre dépôt. À utiliser avec prudence et à comprendre les implications.

---

J'espère que ce guide vous sera utile pour gérer votre projet sur GitHub ! N'hésitez pas si vous avez d'autres questions.
