import { useEffect, useState } from "react";
import { Snackbar, Button, IconButton } from "@mui/material";
import CloseIcon from "@mui/icons-material/Close";

const DISMISSED_KEY = "install_prompt_dismissed";

/**
 * Tarayıcı uygulamayı kurulabilir bulduğunda ekranın altında "Yükle" teklifi
 * gösterir. Böylece bayilerin uygulamayı ana ekrana eklemesi için ayrıca
 * tarif yapmak gerekmez. Kullanıcı kapatırsa bir daha gösterilmez.
 */
export default function InstallPrompt() {
  const [promptEvent, setPromptEvent] = useState(null);

  useEffect(() => {
    if (localStorage.getItem(DISMISSED_KEY)) return undefined;

    const handlePrompt = (event) => {
      // Varsayılan mini çubuğu engelleyip kendi teklifimizi gösteriyoruz.
      event.preventDefault();
      setPromptEvent(event);
    };
    const handleInstalled = () => {
      localStorage.setItem(DISMISSED_KEY, "1");
      setPromptEvent(null);
    };

    window.addEventListener("beforeinstallprompt", handlePrompt);
    window.addEventListener("appinstalled", handleInstalled);
    return () => {
      window.removeEventListener("beforeinstallprompt", handlePrompt);
      window.removeEventListener("appinstalled", handleInstalled);
    };
  }, []);

  const install = async () => {
    if (!promptEvent) return;
    promptEvent.prompt();
    await promptEvent.userChoice;
    setPromptEvent(null);
  };

  const dismiss = () => {
    localStorage.setItem(DISMISSED_KEY, "1");
    setPromptEvent(null);
  };

  return (
    <Snackbar
      open={Boolean(promptEvent)}
      anchorOrigin={{ vertical: "bottom", horizontal: "center" }}
      message="Teklif'i telefonunuza uygulama olarak ekleyin"
      action={
        <>
          <Button color="secondary" size="small" onClick={install}>
            Yükle
          </Button>
          <IconButton size="small" color="inherit" onClick={dismiss} aria-label="Kapat">
            <CloseIcon fontSize="small" />
          </IconButton>
        </>
      }
    />
  );
}
