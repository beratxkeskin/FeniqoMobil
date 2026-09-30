"""
Unit tests for run_local_sync_contract_test.py CLI and Security Guards
"""

import os
import sys
import unittest
from unittest.mock import MagicMock

# supabase/tests dizinini sys.path'e ekle
CURRENT_DIR = os.path.dirname(os.path.abspath(__file__))
if CURRENT_DIR not in sys.path:
    sys.path.insert(0, CURRENT_DIR)

import run_local_sync_contract_test as runner


class TestRunnerCLIAndCleanup(unittest.TestCase):

    def test_default_contract_selection(self):
        """Argüman verilmediğinde varsayılan contract seçilmelidir."""
        selected = runner.parse_args([])
        self.assertEqual(selected, runner.DEFAULT_CONTRACT)
        self.assertEqual(selected, "sync_write_v2_contract.sql")

    def test_explicit_valid_contracts(self):
        """Allowlist'teki tüm sözleşmeler --contract parametresiyle seçilebilmelidir."""
        for contract in runner.ALLOWED_CONTRACTS:
            with self.subTest(contract=contract):
                res_flag = runner.parse_args(["--contract", contract])
                self.assertEqual(res_flag, contract)

    def test_reject_positional_arguments(self):
        """Positional argümanlar kesinlikle reddedilmeli ve SystemExit fırlatmalıdır."""
        for contract in runner.ALLOWED_CONTRACTS:
            with self.subTest(contract=contract):
                with self.assertRaises(SystemExit):
                    runner.parse_args([contract])

    def test_reject_arbitrary_and_traversal_paths(self):
        """Allowlist dışındaki rastgele veya path traversal içeren yollar reddedilmelidir."""
        invalid_inputs = [
            "../../etc/passwd",
            "../migrations/20260816000100_seed_default_categories_v1.sql",
            "unknown_contract.sql",
            "/absolute/path/file.sql",
            "sync_write_v2_contract.sql; DROP TABLE test;"
        ]
        for inv in invalid_inputs:
            with self.subTest(invalid_input=inv):
                with self.assertRaises(SystemExit):
                    runner.parse_args(["--contract", inv])

    def test_resolve_contract_path_validity(self):
        """Allowlist'teki her sözleşmenin çözülen yolu mevcut bir dosya olmalıdır."""
        for contract in runner.ALLOWED_CONTRACTS:
            resolved = runner.resolve_contract_path(contract)
            self.assertTrue(os.path.isfile(resolved), f"Dosya mevcut değil: {resolved}")
            self.assertTrue(resolved.endswith(contract))

    def test_resolve_contract_path_invalid_raises_error(self):
        """Bilinmeyen sözleşme adı ValueError fırlatmalıdır."""
        with self.assertRaises(ValueError):
            runner.resolve_contract_path("invalid_target.sql")

    def test_validate_host_security(self):
        """Yalnızca localhost/127.0.0.1/::1 izinli olmalı, uzak hostlar SystemExit üretmelidir."""
        for local_host in ['localhost', 'LOCALHOST', '127.0.0.1', '::1']:
            runner.validate_host(local_host)

        for remote_host in ['api.supabase.co', '192.168.1.100', 'db.staging.internal', 'example.com']:
            with self.subTest(remote_host=remote_host):
                with self.assertRaises(SystemExit):
                    runner.validate_host(remote_host)

    def test_should_cleanup_roles_logic(self):
        """Rol temizliği karar mantığı veritabanı durumuna göre doğru karar vermelidir."""
        # 1. DB hiç oluşturulamadıysa (db_created=False), oluşturulan roller temizlenmeli
        self.assertTrue(runner.should_cleanup_roles(db_created=False, db_dropped=False))
        self.assertTrue(runner.should_cleanup_roles(db_created=False, db_dropped=True))

        # 2. DB oluşturuldu ve başarıyla silindiyse (db_created=True, db_dropped=True), roller temizlenmeli
        self.assertTrue(runner.should_cleanup_roles(db_created=True, db_dropped=True))

        # 3. DB oluşturuldu fakat silinememişse (db_created=True, db_dropped=False), roller silinmemeli
        self.assertFalse(runner.should_cleanup_roles(db_created=True, db_dropped=False))

    def test_evaluate_role_drop_with_active_dependency(self):
        """Bağımlılığı bulunan oluşturulmuş rol başarı sayılmamalı, silme reddedilmelidir."""
        mock_cur = MagicMock()
        mock_cur.fetchone.return_value = (3,)  # 3 aktif bağımlılık
        success, message = runner.evaluate_role_drop(mock_cur, "test_role")
        self.assertFalse(success)
        self.assertIn("Aktif bağımlılık mevcut", message)
        mock_cur.execute.assert_called_once()  # Sadece dependency sorgulandı, DROP çalıştırılmadı

    def test_evaluate_role_drop_success_when_no_dependency(self):
        """Bağımlılığı olmayan oluşturulmuş rol güvenle silinmelidir."""
        mock_cur = MagicMock()
        mock_cur.fetchone.return_value = (0,)  # 0 bağımlılık
        success, message = runner.evaluate_role_drop(mock_cur, "test_role")
        self.assertTrue(success)
        self.assertIn("Rol güvenle silindi", message)
        self.assertEqual(mock_cur.execute.call_count, 2)  # SELECT + DROP

    def test_evaluate_role_drop_exception_handling(self):
        """Rol silme sırasında istisna fırlatılırsa başarısız dönmelidir."""
        mock_cur = MagicMock()
        mock_cur.execute.side_effect = RuntimeError("DB connection lost")
        success, message = runner.evaluate_role_drop(mock_cur, "test_role")
        self.assertFalse(success)
        self.assertIn("istisna", message)


if __name__ == '__main__':
    unittest.main()
